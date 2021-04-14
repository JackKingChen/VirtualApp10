##判断输入格式是否正确
#if [ ! $1 ];
#then
#echo ''
#echo '///-----------'
#echo '/// 缺少版本号，例如sh build.sh 1.0.0.0'
#echo '///-----------'
#echo ''
#exit 0
#fi

#ScreenShare_Version=$1

set -xe

# Check for required environment variables
env_vars="bamboo_keychain_path bamboo_dev_id_application bamboo_dev_id_installer bamboo_apple_dev_secret_password bamboo_apple_dev_secret_username";
for i in ${env_vars}; do
    if [[ -z "${!i}" ]]; then
        echo "${i} is undefined! Required environment variables: ${env_vars}";
        exit 1;
    fi
done

#进入工程绝对路径
if [ ! $1 ];
then
cd ..
fi
echo ''
echo '///-----------'
echo '///  进入 '$PWD
echo '///-----------'
echo ''

echo 'getSystemVersionInfo'
sw_vers

echo ''

echo 'getXcodeVersionInfo'
xcodebuild -version

echo "Codesigning certificate(s):";
security find-identity -vp codesigning;

echo "Signing keychains:"
systemkeychain -tv

#工程绝对路径
#project_path=$(cd `dirname $0`; pwd)
project_path=$PWD
project_path=${project_path}

#工程名 将XXX替换成自己的工程名
project_name=pmcast

#scheme名 将XXX替换成自己的sheme名
scheme_name="Screen Share"

#打包模式 Debug/Release
development_mode=Release

#info.plist的位置供修改版本号
info_plist_path=${project_path}/pmcast/Info.plist

#pmcast.entitlements的位置
pm_entitlements_path=${project_path}/pmcast/pmcast.entitlements

#derivedDataPath地址设置
derivedData_Path=${project_path}/derivedDataPath

#build地址设置
symroot_path=${project_path}/build

#pkgResources地址设置
pkgResources_path=${project_path}/pkgResources

#ScreenShare app存放的地址
ScreenShare_path=${pkgResources_path}/Screen_Share

# #Soundflower bundle存放的地址
# Soundflower_path=${pkgResources_path}/Soundflower

#BlackHole bundle存放的地址
BlackHole_path=${pkgResources_path}/BlackHole

#build的ScreenShare app存放地址
old_app_path="${symroot_path}/${development_mode}/${scheme_name}.app"

#新的的ScreenShare app存放地址
new_app_path="${ScreenShare_path}/content/${scheme_name}.app"

#tempOutput地址设置
temp_output_path=${project_path}/tempOutput

#output地址设置
output_path=${project_path}/output

echo ''
echo '///-----------'
echo '/// 正在清理工程第一阶段 rm'
echo '///-----------'
echo ''

rm -rf ./derivedDataPath
rm -rf ./build
rm -rf ${ScreenShare_path}/content
rm -rf ./tempOutput
rm -rf ./output

if [ ! -d ./derivedDataPath ];
then
mkdir -p ./derivedDataPath;
fi

if [ ! -d ./build ];
then
mkdir -p ./build;
fi

if [ ! -d ${ScreenShare_path}/content ];
then
mkdir -p ${ScreenShare_path}/content;
fi

if [ ! -d ./tempOutput ];
then
mkdir -p ./tempOutput;
fi

if [ ! -d ./output ];
then
mkdir -p ./output;
fi

echo ''
echo '///-----------'
echo '/// 正在清理工程第二阶段 clean'
echo '///-----------'
echo ''

echo "Signing dependencies start";
for i in "adhoc_core_apple/lib"; do
    pushd "$i"
    echo "Working on directory: ${i}";
    for x in `ls *.dylib`; do
        echo "############################################################";
        echo -n "Signing: ${x} - MD5: ";
        md5 "${x}";
        codesign -dvvv ${x} || true;
        codesign -vvvv --force --sign "${bamboo_dev_id_application}" --preserve-metadata=identifier,entitlements --timestamp "${PWD}/${x}";
        echo -n "Signed MD5: ";
        md5 "${x}";
        codesign -dvvv ${x};
        echo "############################################################";
    done
    popd
done
echo "Signing dependencies end";

echo "Signing BlackHole start"
x="pkgResources/BlackHole/content/BlackHole.driver/Contents/MacOS/BlackHole"
echo "############################################################";
echo "Pre-validating: ${x}";
spctl -a -t exec -vv "${x}" || true;
codesign -dvvv "${x}" || true;
echo "Signing: ${x}";
codesign -vvvv --force --sign "${bamboo_dev_id_application}" --preserve-metadata=entitlements --options runtime --timestamp "${x}";
echo "Signed ${x} - Validating:";
spctl -a -t exec -vv "${x}" || true;
codesign -dvvv "${x}" || true;
echo "############################################################";
echo "Signing BlackHole end"

xcodebuild \
clean \
-workspace ${project_path}/${project_name}.xcworkspace \
-scheme "${scheme_name}" \
-configuration ${development_mode} \
-derivedDataPath ${derivedData_Path}  \
-quiet  || exit

echo ''
echo '///---------'
echo '/// 读取版本号 CFBundleShortVersionString'
echo '///---------'
echo ''

ScreenShare_Version=$(/usr/libexec/PlistBuddy -c "Print CFBundleShortVersionString" ${info_plist_path})
BUILD_CODE=$(/usr/libexec/PlistBuddy -c "Print CFBundleVersion" ${info_plist_path})
# BUILD_CODE=` expr $BUILD_CODE + 1`
# /usr/libexec/PlistBuddy -c "Set CFBundleVersion $BUILD_CODE" ${info_plist_path}

echo ''
echo '///---------'
echo '/// 读取版本号 CFBundleShortVersionString = '${ScreenShare_Version} ' CFBundleVersion = '${BUILD_CODE} '成功'
echo '///---------'
echo ''

#echo ''
#echo '///---------'VERSION
#echo '/// 修改版本号 CFBundleShortVersionString = '${ScreenShare_Version}
#echo '///---------'
#echo ''
#
#/usr/libexec/PlistBuddy -c "Set CFBundleShortVersionString ${ScreenShare_Version}" ${info_plist_path}
#BUILD_CODE=$(/usr/libexec/PlistBuddy -c "Print CFBundleVersion" ${info_plist_path})
#BUILD_CODE=` expr $BUILD_CODE + 1`
#/usr/libexec/PlistBuddy -c "Set CFBundleVersion $BUILD_CODE" ${info_plist_path}
#
#echo ''
#echo '///---------'
#echo '/// 修改版本号 CFBundleShortVersionString = '${ScreenShare_Version} ' CFBundleVersion = '${BUILD_CODE} '成功'
#echo '///---------'
#echo ''

echo ''
echo '///-----------'
echo '/// 正在编译工程:'${development_mode}
echo '///-----------'
echo ''
xcodebuild \
build \
-workspace ${project_path}/${project_name}.xcworkspace \
-scheme "${scheme_name}" \
-configuration ${development_mode} \
-derivedDataPath ${derivedData_Path}  \
SYMROOT=${symroot_path} \
-quiet  || exit

echo ''
echo '///--------'
echo '/// 拷贝app到'${new_app_path}
echo '///--------'
echo ''

cp -R "${old_app_path}" "${new_app_path}"
#cd ${new_app_path}/..
#mv ${scheme_name}.app "ScreenShare.app"

echo ''
echo '///---------'
echo '/// 开始打ScreenShare.pkg包到'${temp_output_path}
echo '///---------'
echo ''

cd ${ScreenShare_path}
echo ''
echo '///-----------'
echo '///  进入 '$PWD
echo '///-----------'
echo ''

echo "Signing Screen Share.app start"
x="content/Screen Share.app";
echo "############################################################";
echo "Pre-validating: ${x}";
codesign -dvvv "${x}" || true;
echo "Signing: ${x}";
codesign -vvvv --force --sign "${bamboo_dev_id_application}" --preserve-metadata=identifier, --deep --entitlements "$pm_entitlements_path" --options runtime --timestamp "${x}";
echo -n "Signed ${x} - Validating...";
codesign -dvvv "${x}" || true;
echo "############################################################";
echo "Signing Screen Share.app end"

pkgbuild \
--root ./content \
--component-plist Screen_Share.plist \
--script ./scripts \
--identifier "cn.com.nd.pmcast" \
--version ${ScreenShare_Version} \
--install-location /Applications \
${temp_output_path}/Screen_Share.pkg \
--quiet || exit

# echo ''
# echo '///---------'
# echo '/// 开始打Soundflower.pkg包到'${temp_output_path}
# echo '///---------'
# echo ''

# cd ${Soundflower_path}
# echo ''
# echo '///-----------'
# echo '///  进入 '$PWD
# echo '///-----------'
# echo ''

# pkgbuild \
# --root ./content \
# --component-plist \
# Soundflower.plist \
# --script ./scripts \
# --identifier com.Cycling74.driver.Soundflower \
# --version 2.0b2 \
# --install-location /Library/Extensions \
# ${temp_output_path}/Soundflower.pkg \
# --quiet || exit

echo ''
echo '///---------'
echo '/// 开始打BlackHole.pkg包到'${temp_output_path}
echo '///---------'
echo ''

cd ${BlackHole_path}
echo ''
echo '///-----------'
echo '///  进入 '$PWD
echo '///-----------'
echo ''

pkgbuild \
--root ./content \
--component-plist \
BlackHole.plist \
--script ./scripts \
--identifier audio.existential.BlackHole \
--version 0.2.6 \
--install-location /Library/Audio/Plug-Ins/HAL \
${temp_output_path}/BlackHole.pkg \
--quiet || exit

cd ${temp_output_path}
echo ''
echo '///-----------'
echo '///  进入 '$PWD
echo '///-----------'
echo ''

echo ''
echo '///---------'
echo '/// 拷贝Resources到'${temp_output_path}
echo '///---------'
echo ''

cp -R ${pkgResources_path}/Resources ${temp_output_path}

echo '///---------'
echo '/// 拷贝Distribution到'${temp_output_path}
echo '///---------'
echo ''

cp -R ${pkgResources_path}/Distribution ${temp_output_path}

echo ''
echo '///---------'
echo '/// 修改Distribution的版本号'
echo '///---------'
echo ''

sed -i '' 's/version="0.0.0"/version="'${ScreenShare_Version}'"/' Distribution

echo ''
echo '///---------'
echo '/// 开始打mpkg到'${temp_output_path}
echo '///---------'
echo ''

productbuild \
--distribution Distribution \
--resources ./Resources \
--package-path ./Packages \
"Screen Share_${ScreenShare_Version}.pkg" \
--quiet || exit

echo ''
echo '///---------'
echo '/// 打mpkg到'${temp_output_path}'成功'
echo '///---------'
echo ''

echo ''
echo '///---------'
echo '/// 开始解压ScreenShare_${ScreenShare_Version}.pkg'
echo '///---------'
echo ''

pkgutil \
--expand \
"Screen Share_${ScreenShare_Version}.pkg" \
${output_path}/expand \

echo ''
echo '///---------'
echo '/// 解压ScreenShare_${ScreenShare_Version}.pkg成功'
echo '///---------'
echo ''

cd ${output_path}
echo ''
echo '///-----------'
echo '///  进入 '$PWD
echo '///-----------'
echo ''

echo ''
echo '///---------'
echo '/// 开始修改conclusion.rtf为conclusion.rtfd'
echo '///---------'
echo ''

cp -R ${pkgResources_path}/Replace/Resources ${output_path}/expand

echo ''
echo '///---------'
echo '/// 修改conclusion.rtf为conclusion.rtfd成功'
echo '///---------'
echo ''

echo ''
echo '///---------'
echo '/// 开始压缩ScreenShare_${ScreenShare_Version}_modify.pkg'
echo '///---------'
echo ''

pkgutil \
--flatten ./expand/ \
"Screen Share_${ScreenShare_Version}_modify.pkg" \

echo ''
echo '///---------'
echo '/// 压缩ScreenShare_${ScreenShare_Version}_modify.pkg成功'
echo '///---------'
echo ''

echo ''
echo '///---------'
echo '/// 开始签名ScreenShare_${ScreenShare_Version}_sign.pkg'
echo '///---------'
echo ''

productsign \
--sign "${bamboo_dev_id_installer}" \
--keychain "${bamboo_keychain_path}" \
"Screen Share_${ScreenShare_Version}_modify.pkg" \
"Screen Share_${ScreenShare_Version}_sign.pkg" \

echo ''
echo '///---------'
echo '/// 签名ScreenShare_${ScreenShare_Version}_sign.pkg成功'
echo '///---------'
echo ''

#echo ''
#echo '///---------'
#echo '/// 更新git'
#echo '///---------'
#echo ''
#
#cd ${project_path}
#echo ''
#echo '///-----------'
#echo '///  进入 '$PWD
#echo '///-----------'
#echo ''

#git add .
#git commit -m 'update version'
#git push
#git tag -a -m "update version" ${ScreenShare_Version}-${BUILD_CODE}
#git push --tags

#echo ''
#echo '///---------'
#echo '/// 更新git成功'
#echo '///---------'
#echo ''

cd ${output_path}
echo ''
echo '///-----------'
echo '///  进入 '$PWD
echo '///-----------'
echo ''

echo ''
echo '///---------'
echo '/// 上传pkg到notarize service'
echo '///---------'
echo ''

xcrun altool \
--notarize-app \
--primary-bundle-id "cn.com.nd.pmcast" \
--username "${bamboo_apple_dev_secret_username}" \
--password "${bamboo_apple_dev_secret_password}" \
--file "Screen Share_${ScreenShare_Version}_sign.pkg" \
--asc-provider "Promethean" \
&> tmp

uuid=`cat tmp | grep -Eo '\w{8}-(\w{4}-){3}\w{12}$'`
echo ''
echo "upload pkg completed, uuid=[$uuid]"
echo ''

set +e

while true; do
    echo "checking for notarization..."
 
    xcrun altool \
    --notarization-info "$uuid" \
    --username "${bamboo_apple_dev_secret_username}" \
    --password "${bamboo_apple_dev_secret_password}" \
    &> tmp
    
    r=`cat tmp`
    t=`echo "$r" | grep -i "success"`
    f=`echo "$r" | grep -i "invalid"`

    if [[ "$t" != "" ]]; then
        echo "notarization done!"
        xcrun stapler staple "Screen Share_${ScreenShare_Version}_sign.pkg"
        echo "stapler done!"
        break
    fi
    
    if [[ "$f" != "" ]]; then
        echo "$r"
        exit 1
    fi
    
    echo "not finish yet, sleep 1m then check again..."
    sleep 60
    
done

exit 0


