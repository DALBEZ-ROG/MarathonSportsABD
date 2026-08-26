// -----------------------------------------------------------------------------
// Entorno de PRODUCCION (lote L15, defecto D-10)
// -----------------------------------------------------------------------------
// Hasta la L15 este fichero era identico al de desarrollo:
//
//     export const environment = { production: true, apiUrl: 'http://localhost:8080/api' };
//
// Es decir: el artefacto de produccion llamaba a localhost —no funciona fuera de
// la maquina del desarrollador— y ademas por HTTP plano, con lo que el JWT
// viajaba sin cifrar. Contrastaba con el esfuerzo puesto en TLS entre la
// aplicacion y la base (sslmode=verify-full), que quedaba anulado en el tramo
// navegador -> backend.
//
// Ahora la URL es RELATIVA. El navegador la resuelve contra el mismo origen
// desde el que se sirvio la aplicacion, asi que:
//
//   - hereda el esquema (https si la pagina va por https),
//   - no hay que recompilar para cambiar de servidor,
//   - y desaparece el CORS entre front y back.
//
// Requiere que un proxy inverso (nginx, Apache, IIS...) sirva el frontend y
// reenvie /api al backend. Ejemplo minimo de nginx:
//
//     location /        { root /var/www/marathon; try_files $uri $uri/ /index.html; }
//     location /api/    { proxy_pass http://127.0.0.1:8080; }
//
// Si en algun despliegue el backend vive en otro dominio, hay que poner aqui la
// URL absoluta CON https y anadir ese origen a corsConfigurationSource() en
// SecurityConfig, que hoy solo admite http://localhost:4200.
export const environment = {
  production: true,
  apiUrl: '/api'
};
