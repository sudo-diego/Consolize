#!/bin/bash
# Capturas de la app en un emulador de tablet (lo corre .github/workflows/capturas.yml). Navega con botones del control
# (LB/RB, A, B) como lo haría alguien en la tablet y guarda cada pantalla en shots/.
set -x
PKG=io.github.sudodiego.g9control
APK=app/build/outputs/apk/debug/app-debug.apk
mkdir -p shots
adb root; sleep 4
adb install -r "$APK"
# Horizontal, como la tablet (la Pixel Tablet ya es horizontal en 0), y sin el aviso de "pantalla completa" de Android
# (tapaba la app y se quedaba con los botones).
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell settings put secure immersive_mode_confirmations confirmed
# Abrir una vez (crea su carpeta), cerrar y dejar hecha la configuración inicial.
adb shell am start -n $PKG/.MainActivity; sleep 8
adb shell am force-stop $PKG
APPUID=$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')
adb shell mkdir -p /data/data/$PKG/shared_prefs
printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n<boolean name=\"setup_done\" value=\"true\" />\n<string name=\"lang\">ES</string>\n</map>\n" > /tmp/g9.xml
adb push /tmp/g9.xml /data/data/$PKG/shared_prefs/g9.xml
adb shell chown -R $APPUID:$APPUID /data/data/$PKG/shared_prefs
adb shell chmod 771 /data/data/$PKG/shared_prefs
adb shell chmod 660 /data/data/$PKG/shared_prefs/g9.xml
# El servicio de accesibilidad encendido (si no, Controles sale atenuado).
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.AutoModeService
adb shell settings put secure accessibility_enabled 1
adb shell am start -n $PKG/.MainActivity; sleep 12
# Por si igual salió el aviso de Android: tocar "Entendido" no hace falta, se cierra con Atrás del sistema sin salir.
shot() { sleep "${2:-2}"; adb exec-out screencap -p > "shots/$1.png"; }
key() { adb shell input keyevent "$1"; }
tap() { adb shell input tap "$1" "$2"; }
# Toques en el dock (coordenadas de la Pixel Tablet en horizontal, 2560 × 1600).
DOCK_Y=1517
shot 1-inicio 1
tap 1075 $DOCK_Y; shot 2-biblioteca
tap 1307 $DOCK_Y; shot 3-panel 3
tap 1030 375;     shot 3b-editar-mosaicos 6
shot 3c-editar-mosaicos-2 3
tap 1522 $DOCK_Y; shot 4-control
tap 1349 680;     shot 5-boton-guia 3
tap 1728 $DOCK_Y; shot 6-mas
tap 420 800;      shot 7-mas-menu
adb logcat -d | grep -iE "AndroidRuntime|FATAL|g9control|Compose" | tail -200 > shots/log.txt
ls -la shots
