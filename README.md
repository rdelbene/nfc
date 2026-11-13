# Lector NFC

App Android que, al acercar una tarjeta, **detecta el tipo** y muestra en pantalla **todo el contenido legible**. Solo lectura.

## Qué muestra
- **UID** y lista de tecnologías de la tarjeta.
- **Tipo probable**: Mifare Classic / Ultralight / NTAG, ISO 14443-4 (bancarias, transporte, documentos), FeliCa, ISO 15693.
- Datos de bajo nivel según la tecnología: ATQA, SAK, ATS, system code, etc.
- **NDEF**: los registros grabados (texto, URL…).
- **Mifare Ultralight / NTAG**: volcado de todas las páginas en hexadecimal.
- **Mifare Classic**: intenta las **claves públicas por defecto** y vuelca los bloques de los sectores que abren. Los sectores con clave propia se marcan como bloqueados (no intenta romperlas).

## Compilar
No hace falta instalar nada: cada vez que se sube un cambio, **GitHub Actions** compila y firma el APK y lo publica en **Releases → latest** (archivo `LectorNFC.apk`). Firma fija, así las versiones nuevas se instalan encima de la anterior.

## Instalar
Bajá `LectorNFC.apk` de Releases, pasalo al celu y:
```
adb install LectorNFC.apk
```
Requiere Android 7 o superior y un teléfono con NFC.

## Uso
Abrí la app, activá el NFC y apoyá una tarjeta en la parte de atrás del teléfono. El contenido aparece en pantalla y se puede seleccionar y copiar.
