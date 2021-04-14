#!/bin/sh

cp ~/work/nd-hardcasting-mac-sender/hwcasting/build-HwCasting-static_mini-Release/HwCasting.app/Contents/MacOS/HwCasting HwCasting.app/Contents/MacOS/
cp ~/work/nd-hardcasting-mac-sender/hwcasting/build-HwCasting-static_mini-Release/HwCasting.app/Contents/Resources/en.qm HwCasting.app/Contents/Resources/
codesign -vvvv --force --sign "Developer ID Application: Mingcai han (MZ4M2GFKCS)" --preserve-metadata=identifier, --deep --entitlements ./HwCasting.entitlements --options runtime --timestamp HwCasting.app/Contents/MacOS/HwCasting
#codesign -vvvv --force --sign "Developer ID Application: Mingcai han (MZ4M2GFKCS)" --preserve-metadata=identifier, --deep --entitlements ./HwCasting.entitlements --options runtime --timestamp HwCasting.app/Contents/MacOS/en.qm
codesign -vvvv --force --sign "Developer ID Application: Mingcai han (MZ4M2GFKCS)" --preserve-metadata=identifier, --deep --entitlements ./HwCasting.entitlements --options runtime --timestamp HwCasting.app
./foldermd5 HwCasting.app > HwCastingLauncher.app/Contents/Resources/md5.txt 
rm HwCastingLauncher.app/Contents/Resources/HwCasting.app.zip
zip -q -r -y -9 HwCastingLauncher.app/Contents/Resources/HwCasting.app.zip HwCasting.app

xcrun altool --notarize-app --primary-bundle-id "com.adhoc.hd.screencast" --username "nihaoahmc@126.com" --password "fisr-audi-kiwa-gqwg" --file ./HwCastingLauncher.app/Contents/Resources/HwCasting.app.zip &> tmp

uuid=`cat tmp | grep -Eo '\w{8}-(\w{4}-){3}\w{12}$'`
echo ''
echo "upload hwcasting pkg completed, uuid=[$uuid]"
echo ''

set +e

while true; do
    echo "checking for notarization..."
 
    xcrun altool --notarization-info "$uuid" --username "nihaoahmc@126.com" --password "fisr-audi-kiwa-gqwg" &> tmp
    
    r=`cat tmp`
    t=`echo "$r" | grep -i "success"`
    f=`echo "$r" | grep -i "invalid"`

    if [[ "$t" != "" ]]; then
        echo "notarization done!"
       #xcrun stapler staple "./HwCastingLauncher.app/Contents/Resources/HwCasting.app.zip"
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

codesign -vvvv --force --sign "Developer ID Application: Mingcai han (MZ4M2GFKCS)" --preserve-metadata=identifier, --deep --entitlements ./HwCasting.entitlements --options runtime --timestamp HwCastingLauncher.app/Contents/Resources/HwCasting.app.zip
codesign -vvvv --force --sign "Developer ID Application: Mingcai han (MZ4M2GFKCS)" --preserve-metadata=identifier, --deep --entitlements ./HwCasting.entitlements --options runtime --timestamp HwCastingLauncher.app
zip -q -r -y -9 HwCastingLauncher.zip HwCastingLauncher.app 

xcrun altool --notarize-app --primary-bundle-id "com.adhoc.hd.screencast.launcher" --username "nihaoahmc@126.com" --password "fisr-audi-kiwa-gqwg" --file ./HwCastingLauncher.zip &>tmpl

uuid=`cat tmpl | grep -Eo '\w{8}-(\w{4}-){3}\w{12}$'`
echo ''
echo "upload hwcastinglauncher pkg completed, uuid=[$uuid]"
echo ''

set +e

while true; do
    echo "checking for notarization..."
 
    xcrun altool --notarization-info "$uuid" --username "nihaoahmc@126.com" --password "fisr-audi-kiwa-gqwg" &> tmpl
    
    r=`cat tmpl`
    t=`echo "$r" | grep -i "success"`
    f=`echo "$r" | grep -i "invalid"`

    if [[ "$t" != "" ]]; then
        echo "notarization done!"
        #xcrun stapler staple "HwCastingLauncher.zip"
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



#sleep 1

#tar -czvf out/HwCastingLauncher`date +%m%d%H%M`.app.tgz HwCastingLauncher.app




