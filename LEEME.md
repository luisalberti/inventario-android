# App Android — Inventario

App para Android 8.0+ (funciona en Android 13). Escanea QR con la
cámara, precarga los datos del producto desde el backend y confirma
la venta con boleta o factura. Si es factura, el backend envía email
al administrador con los datos.

## Cómo obtener el APK

Ya tienes el proyecto compilándose en GitHub Actions. Cada vez que
subes un cambio, GitHub compila un APK nuevo en ~5 min. Lo bajas de:

- Pestaña **Releases** del repo (si activaste el permiso "Read and
  write" en Settings → Actions → General).
- O de la pestaña **Actions** → último run → sección **Artifacts** al
  fondo → descarga zip "Inventario-APK" → contiene el `.apk`.

## Instalar en el celular

1. Copia el `.apk` al celular (email, WhatsApp, USB).
2. Ábrelo. Android te dirá "instalar de esta fuente" — activa el
   permiso y sigue.
3. Al abrir la app, primera pantalla pide:
   - **URL del servidor**: la que ves en tu dashboard del PC.
   - **Usuario**: `admin` (o el que crees desde `/docs`).
   - **Contraseña**: la del `config.json` del PC. Puedes ver/ocultar
     la contraseña mientras la escribes con el ícono del ojo.

## Conexión — dos rutas

- **En Wi-Fi de la oficina**: usa la URL LAN que aparece en el
  dashboard, tipo `http://192.168.1.7:8000`. Rápido, no depende de
  internet.
- **Desde datos móviles o fuera de la oficina**: usa el
  `ACCESO_EXTERNO.bat` del PC — genera una URL pública HTTPS de
  Cloudflare tipo `https://xxx.trycloudflare.com` para poner en la
  app. Ver el README del PC.

Cada celular guarda su URL en la config. Puedes tener celulares en
LAN y otros en internet al mismo tiempo.

## Flujo de venta

1. Botón grande "Escanear QR" → cámara.
2. Escanea → trae nombre, SKU y precio de venta del producto.
3. Se agrega al carrito. Puedes escanear varios productos.
4. "Continuar con la venta" → formulario:
   - Boleta o Factura (si factura, RUT + razón social + dirección + giro).
   - Efectivo / Tarjeta / Transferencia (efectivo pide monto y muestra vuelto).
5. Confirmar → backend descuenta stock, actualiza Excel maestro (si
   está configurado) y envía email de factura al admin.
