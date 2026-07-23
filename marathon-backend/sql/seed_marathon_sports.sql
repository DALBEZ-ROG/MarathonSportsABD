-- ============================================================
-- SEED DATA — Marathon Sports (mod_venta_inve)
-- Datos de negocio reales curados desde Excel de operaciones
-- NO incluye roles/permisos/admin (los crea DataInitializer)
-- Ejecutar UNA sola vez sobre la BD ya migrada
-- ============================================================

BEGIN;


-- ─── CIUDADES ───
INSERT INTO ciudad (nombre, estado) VALUES ('24 De Mayo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Amaguaña', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Ambato', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Arenillas', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Atacames', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Atuntaqui', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Baba', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Babahoyo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Bahia De Caraquez', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Baños De Agua Santa', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Biblian', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Bucay', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Calceta', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Calderon', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Cayambe', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Cañar', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Chimbo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Chone', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Conocoto', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Cuenca', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Cumbaya', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Daule', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Duran', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('El Carmen', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('El Coca', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('El Empalme', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('El Triunfo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Esmeraldas', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Flavio Alfaro', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Francisco De Orellana', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Giron', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Gualaquiza', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Guaranda', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Guayaquil', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Huaquillas', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Ibarra', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Isidro Ayora', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Jama', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Jipijapa', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Junin', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('La Concordia', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('La Joya De Los Sachas', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('La Libertad', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Lago Agrio', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Latacunga', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Loja', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Loreto', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Macas', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Machachi', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Machala', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Manta', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Milagro', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Naranjal', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Naranjito', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Nobol', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Otavalo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Pedernales', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Pedro Carbo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Pichincha Manabi', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Piñas', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Playas', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Pomasqui', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Portoviejo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Puembo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Puerto Quito', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Puyo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Quevedo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Quito', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Riobamba', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Rocafuerte', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Salinas', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Samborondon', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('San Miguel De Bolivar', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('San Rafael', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Santa Rosa', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Santo Domingo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Shushufindi', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Tababela', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Tabacundo', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Tena', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Tosagua', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Tulcan', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Tumbaco', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Valdivia', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Ventanas', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Vinces', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Zamora', 'activo');
INSERT INTO ciudad (nombre, estado) VALUES ('Zaruma', 'activo');

-- ─── CATEGORIAS ───
INSERT INTO categoria (nombre, descripcion) VALUES ('Calzado', 'Zapatos deportivos y casuales');
INSERT INTO categoria (nombre, descripcion) VALUES ('Ropa', 'Prendas deportivas y casuales');
INSERT INTO categoria (nombre, descripcion) VALUES ('Accesorios', 'Complementos y accesorios deportivos');

-- ─── UNIDADES DE MEDIDA ───
INSERT INTO unidad_medida (nombre, abreviatura) VALUES ('Unidad', 'UND');
INSERT INTO unidad_medida (nombre, abreviatura) VALUES ('Par', 'PAR');
INSERT INTO unidad_medida (nombre, abreviatura) VALUES ('Caja', 'CJA');

-- ─── PROVEEDORES ───
INSERT INTO proveedor (nombre, contacto, correo, telefono, direccion, estado) VALUES ('Nike Ecuador S.A.', 'Departamento Comercial', 'ventas@nike.com', '02-2670487', 'Quito, Ecuador', 'activo');
INSERT INTO proveedor (nombre, contacto, correo, telefono, direccion, estado) VALUES ('Adidas Andina', 'Departamento Comercial', 'ventas@adidas.com', '02-2116739', 'Quito, Ecuador', 'activo');
INSERT INTO proveedor (nombre, contacto, correo, telefono, direccion, estado) VALUES ('Puma Sports', 'Departamento Comercial', 'ventas@puma.com', '02-2026225', 'Quito, Ecuador', 'activo');
INSERT INTO proveedor (nombre, contacto, correo, telefono, direccion, estado) VALUES ('Under Armour EC', 'Departamento Comercial', 'ventas@under.com', '02-2777572', 'Quito, Ecuador', 'activo');
INSERT INTO proveedor (nombre, contacto, correo, telefono, direccion, estado) VALUES ('Distribuidora Marathon', 'Departamento Comercial', 'ventas@distribuidora.com', '02-2288389', 'Quito, Ecuador', 'activo');
INSERT INTO proveedor (nombre, contacto, correo, telefono, direccion, estado) VALUES ('Reebok Distribución', 'Departamento Comercial', 'ventas@reebok.com', '02-2256787', 'Quito, Ecuador', 'activo');

-- ─── PRODUCTOS ───
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP ADI JI0004 F50 LEAGUE FGM 10', 'Marca: ADIDAS', 60.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP ADI JI2678 SAMBA OG W 9', 'Marca: ADIDAS', 64.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK HQ1966-001 AIR FORCE 1  0 8', 'Marca: NIKE', 130.89, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP RBK 100232300 REEBOK BB 1000 11', 'Marca: REEBOK', 53.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'BOT HIT H007941-021-005 M TARANTULA LT 1', 'Marca: HI-TEC', 54.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK DM0113-100 W NIKE COURT V 5', 'Marca: NIKE', 87.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK FV6343-010 W NIKE PROMINA 5', 'Marca: NIKE', 86.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK IH2063-200 AIR MAX 90 ESS 10.5', 'Marca: NIKE', 112.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP HKA 1162031-GRTM W CLIFTON 10 7', 'Marca: HOKA ONE ONE', 115.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP ADI JH8691 GRAND COURT 2 12', 'Marca: ADIDAS', 54.98, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP ADI JR4613 GRAND COURT BA 12', 'Marca: ADIDAS', 47.5, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK HF6279-003 TEAM HUSTLE D 4.5Y', 'Marca: NIKE', 40.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK DV5456-008 COURT BOROUGH 5Y', 'Marca: NIKE', 41.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK DV5456-008 COURT BOROUGH 6Y', 'Marca: NIKE', 41.48, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP ADI JQ3036 VL COURT BASE 8.5', 'Marca: ADIDAS', 66.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP UND 6006723-011 UA ASSERT 11 9', 'Marca: UNDER ARMOUR', 79.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP ADI JQ9996 HOOPS 40 MID 9.5', 'Marca: ADIDAS', 46.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK FQ1458-600 ZM VAPOR 16 AC 9', 'Marca: NIKE', 60.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK FB2599-107 AIR ZOOM G.T. 9', 'Marca: NIKE', 71.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK FD2723-113 W AIR ZOOM PEG 6', 'Marca: NIKE', 84.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK HM6803-301 NIKE VOMERO 18 9.5', 'Marca: NIKE', 159.98, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP RBK 100009940 CLUB C 85 8', 'Marca: REEBOK', 74.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK FQ8449-001 ZOOM VAPOR 16 7.5', 'Marca: NIKE', 60.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK HJ9198-401 NIKE REVOLUTIO 10', 'Marca: NIKE', 80.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK HF6279-002 TEAM HUSTLE D 5Y', 'Marca: NIKE', 55.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK FJ7791-500 G.T. HUSTLE AC 9.5', 'Marca: NIKE', 60.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP RBK 100244418 SPLIT FLEX 6', 'Marca: REEBOK', 69.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP MUN 4034064 PADX 64 PADEL 8.5', 'Marca: MUNICH', 79.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP UMB 82293U-NH4 CLASSICO XIV L 8.5', 'Marca: UMBRO', 40.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP ADI JR9325 CRAZYQUICK LS 8', 'Marca: ADIDAS', 82.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP JRD FZ9854-001 WMNS JORDAN FL 6.5', 'Marca: JORDAN', 91.79, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK FQ1831-300 M NIKE MC TRAI 9', 'Marca: NIKE', 64.17, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP PUM 311730 09 SKYROCKET LITE 8.5', 'Marca: PUMA', 64.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP NIK HQ2052-401 NIKE PACIFIC 10', 'Marca: NIKE', 91.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (1, 2, 'ZAP UMB 82176U-090 CLASSICO XIII 8.5', 'Marca: UMBRO', 79.98, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC MAS 068CMC04762BLNA F JUE ALT2 T XL', 'Marca: MARATHON SPORTS', 79.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC MAS 068CMC04763BLNA FEF JUE ALT2 T L', 'Marca: MARATHON SPORTS', 79.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC MAS 068CMC04763BLNA FEF JUE ALT2 T X', 'Marca: MARATHON SPORTS', 79.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC MAS 068CMC005112AMNA BSC JUE OFI TM', 'Marca: MARATHON SPORTS', 74.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'SHT AST 069SHT00019NENA OPA XL', 'Marca: ASTRO', 24.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC OUT OUTCMC10047ZMNA GARDEN L', 'Marca: OUTLAND', 10.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC PUM 660321 10 NEYMAR JR PLAY M', 'Marca: PUMA', 27.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC RBK 100240712 ANDY SS TEE L', 'Marca: REEBOK', 18.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC ADI JZ5047 M ANX RUN T L', 'Marca: ADIDAS', 24.5, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC ADI JW4342 M PBJ T L', 'Marca: ADIDAS', 24.5, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'SHT NIK NESSE471-018 NIKE OFF SHORE 32', 'Marca: NIKE', 43.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC ADI JW0160 GRAPHIC LO SS L', 'Marca: ADIDAS', 26.5, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'HDY NIK HV1439-298 M NK CLUB WINT L', 'Marca: NIKE', 61.75, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC ADI IY7423 TREFOIL TEE S', 'Marca: ADIDAS', 13.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC ADI IY4003 TREFOIL TEE S', 'Marca: ADIDAS', 13.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'SHT VOL VA081241-NVY ESSENTIAL AMPH 32', 'Marca: VOLCOM', 39.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC ADI JF1096 M SL SJ T M', 'Marca: ADIDAS', 23.09, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC MAS 068CMC04738AMZO F JUE OFI TM L', 'Marca: MARATHON SPORTS', 79.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC ADI JJ4298 BOCA H JSY 2XL', 'Marca: ADIDAS', 75.59, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC NIK HJ4606-680 PSG M NK DF JS 2XL', 'Marca: NIKE', 56.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC NIK HJ3580-017 M NK DFADV SOL M', 'Marca: NIKE', 43.79, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'MLC AST 069CML00019POPO BRAT S', 'Marca: ASTRO', 16.19, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CML MAS 068CML00290BLNA FEF JUE ALT2 T M', 'Marca: MARATHON SPORTS', 84.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC MAS 068CMC04739AMZO FEF JUE OFI TM S', 'Marca: MARATHON SPORTS', 79.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC ADI KH3934 AFA H JSY 10 A L', 'Marca: ADIDAS', 184.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'JKT ADI JM0139 MT CW T FZ FL S', 'Marca: ADIDAS', 50.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC MAS 068CMC04750ZONA F JUE ALT1 T 2XL', 'Marca: MARATHON SPORTS', 79.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC MAS 068CMC04766BLNA FEF JUE ALT2 E M', 'Marca: MARATHON SPORTS', 49.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC ADI JM8978 M TR CAT G T M', 'Marca: ADIDAS', 23.98, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'PAN NIK FN2405-610 M NK DF STRK P M', 'Marca: NIKE', 49.8, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'BVD RBK APRUN007 ID RUNNING TAN L', 'Marca: REEBOK', 20.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC ADI JD7413-T H JSY M', 'Marca: ADIDAS', 99.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC MAS 068CMC04756ZONA FEF JUE ALT1 E 1', 'Marca: MARATHON SPORTS', 39.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'JKT MCK 432362-485 ALIX G XS', 'Marca: MCKINLEY', 38.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (2, 1, 'CMC UND 6006763-308 PJT RCK HWT OS M', 'Marca: UNDER ARMOUR', 49.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'CAN NIK DN3611-100 NK MERC LITE M', 'Marca: NIKE', 20.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'PEL WIL WTB9300-XB07 NBA DRV BSKT S 7', 'Marca: WILSON', 23.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MRL JRD 9A0631-023 JAN AIRBORNE F TU', 'Marca: JORDAN', 11.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MTN NIK CU8089-010 NK ACDMY TEAM TU', 'Marca: NIKE', 46.2, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'RAQ BAB 150174/100 VIPER JL 30 TU', 'Marca: BABOLAT', 319.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'RAQ BAB 150165-100 TECHNICAL VERT TU', 'Marca: BABOLAT', 119.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MOH ADI JD9563 CLSC BARS 3S TU', 'Marca: ADIDAS', 34.39, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'BTR ADI JE8346 LINEAR WALLET TU', 'Marca: ADIDAS', 23.97, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'BOL PUM 092180 02 UP SLOUCHY HOB TU', 'Marca: PUMA', 27.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'PEL PUM 084894 01 PUMA ORBITA UL #5', 'Marca: PUMA', 84.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'PEL NIK HV6332-803 NK PARK TEAM 5', 'Marca: NIKE', 17.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'GRA UND 1369815-651 PROJECT ROCK T TU', 'Marca: UNDER ARMOUR', 34.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'PEL NIK N101251901407 NIKE PLAYGROUN 7', 'Marca: NIKE', 24.5, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'BDA GMP GYMBDA10002SUNA RESISTANCE BAN T', 'Marca: GYM POWER', 16.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MED AST 069MED00454BLNA MEN WHITE LOW 8-', 'Marca: ASTRO', 11.19, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'GUA NIK N1012314091MD NIKE W GYM ESS M', 'Marca: NIKE', 19.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'GRA JRD HV1169-010 U J PRO CAP S S/M', 'Marca: JORDAN', 21.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'PEL JRD J100825505507 JORDAN PLAYGRO 7', 'Marca: JORDAN', 20.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MOH JNS JS0A4QVALH4 RIGHT PACK TU', 'Marca: JANSPORT', 46.79, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MAL ADI KE6245 TRA DEF DUF XS TU', 'Marca: ADIDAS', 37.59, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'CAG OUT 132CAG00034ACNA ROCKREST TU', 'Marca: OUTLAND', 18.39, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'GRA PUM 026734 01 TRAINING FLEXF M/L', 'Marca: PUMA', 10.01, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MED ADI JV7441 LOW CUT S 3P L', 'Marca: ADIDAS', 11.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MED ADI JV7442 LOW CUT S 3P L', 'Marca: ADIDAS', 11.49, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'CDA ENE 270700-902 050 SPEED ROPE 1.0 TU', 'Marca: ENERGETICS', 6.41, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MOH ADI IS7066 CLSC BP DAY TU', 'Marca: ADIDAS', 24.48, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'CAN NIK SP0040-635 NK J GUARD M', 'Marca: NIKE', 9.09, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'LON JNS JS0A352LAO3 BIG BREAK TU', 'Marca: JANSPORT', 16.79, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MED ADI JV7401 3S CREW S 3P M', 'Marca: ADIDAS', 13.79, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'PEL SIU 106741 NEO BALLS BOTE 106759 TU', 'Marca: SIUX', 17.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'COR SIU 114542 SWITCH STRAP TU', 'Marca: SIUX', 9.98, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'BUF MAS 068BUF00048SUNA FEF BUF 2026 M', 'Marca: MARATHON SPORTS', 19.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'BEN UFC UHK-69773 UFC CONTENDER TU', 'Marca: UFC', 13.0, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'MUN JRD JKN01010OS JORDAN JUMPMAN TU', 'Marca: JORDAN', 11.99, 'activo');
INSERT INTO producto (id_categoria, id_unidad_medida, nombre, descripcion, precio, estado) VALUES (3, 1, 'CAN PUM 030635 01 VENTILATION SH S', 'Marca: PUMA', 5.0, 'activo');

-- ─── PRODUCTO_PROVEEDOR ───
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (1, 2, 36.0, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (2, 2, 38.4, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (3, 6, 78.53, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (4, 1, 32.39, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (5, 6, 32.4, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (6, 6, 52.49, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (7, 5, 52.19, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (8, 1, 67.49, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (9, 5, 69.29, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (10, 4, 32.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (11, 1, 28.5, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (12, 1, 24.0, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (13, 1, 24.89, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (14, 2, 24.89, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (15, 2, 39.89, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (16, 5, 47.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (17, 5, 27.6, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (18, 1, 36.0, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (19, 5, 43.19, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (20, 2, 50.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (21, 6, 95.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (22, 6, 44.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (23, 6, 36.0, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (24, 5, 48.0, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (25, 4, 33.59, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (26, 2, 36.0, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (27, 4, 41.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (28, 5, 47.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (29, 3, 24.0, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (30, 1, 49.49, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (31, 2, 55.07, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (32, 6, 38.5, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (33, 4, 38.4, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (34, 3, 55.19, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (35, 3, 47.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (36, 2, 47.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (37, 2, 47.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (38, 3, 47.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (39, 1, 44.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (40, 1, 14.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (41, 4, 6.29, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (42, 1, 16.79, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (43, 3, 10.8, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (44, 3, 14.7, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (45, 5, 14.7, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (46, 3, 26.39, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (47, 1, 15.9, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (48, 6, 37.05, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (49, 4, 8.09, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (50, 5, 8.09, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (51, 1, 23.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (52, 4, 13.85, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (53, 1, 47.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (54, 5, 45.35, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (55, 3, 33.89, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (56, 6, 26.27, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (57, 5, 9.71, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (58, 3, 50.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (59, 5, 47.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (60, 2, 110.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (61, 6, 30.59, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (62, 1, 47.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (63, 1, 29.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (64, 6, 14.39, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (65, 2, 29.88, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (66, 3, 12.59, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (67, 1, 59.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (68, 2, 23.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (69, 1, 23.39, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (70, 4, 29.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (71, 3, 12.59, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (72, 4, 14.39, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (73, 6, 7.19, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (74, 3, 27.72, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (75, 2, 191.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (76, 3, 71.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (77, 3, 20.63, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (78, 2, 14.38, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (79, 6, 16.79, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (80, 3, 50.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (81, 6, 10.49, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (82, 6, 20.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (83, 6, 14.7, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (84, 1, 10.19, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (85, 5, 6.71, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (86, 6, 11.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (87, 2, 12.89, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (88, 5, 12.59, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (89, 6, 28.07, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (90, 2, 22.55, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (91, 2, 11.03, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (92, 4, 6.01, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (93, 4, 6.89, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (94, 3, 6.89, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (95, 6, 3.85, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (96, 6, 14.69, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (97, 5, 5.45, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (98, 2, 10.07, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (99, 6, 8.27, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (100, 3, 10.79, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (101, 1, 5.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (102, 2, 11.99, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (103, 1, 7.8, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (104, 3, 7.19, true, 'activo');
INSERT INTO producto_proveedor (id_producto, id_proveedor, precio_compra, es_proveedor_principal, estado) VALUES (105, 4, 3.0, true, 'activo');

-- ─── BODEGAS ───
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (20, 'Bodega AAM1', 'Centro de distribución PROV', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (68, 'Bodega ABS1', 'Centro de distribución PROV', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (34, 'Bodega ACO1', 'Centro de distribución UIO', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (51, 'Bodega ACU1', 'Centro de distribución PROV', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (20, 'Bodega ACY1', 'Centro de distribución GYE', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (34, 'Bodega AEM1', 'Centro de distribución GYE', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (3, 'Bodega AJA1', 'Centro de distribución UIO', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (3, 'Bodega ASC1', 'Centro de distribución UIO', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (3, 'Bodega ASO1', 'Centro de distribución GYE', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (34, 'Bodega AVI1', 'Centro de distribución GYE', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (20, 'Bodega BAM1', 'Centro de distribución PROV', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (34, 'Bodega BAT1', 'Centro de distribución UIO', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (34, 'Bodega BBA1', 'Centro de distribución GYE', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (51, 'Bodega BCO1', 'Centro de distribución UIO', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (51, 'Bodega BLJ1', 'Centro de distribución PROV', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (20, 'Bodega BNJ1', 'Centro de distribución PROV', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (51, 'Bodega BNO1', 'Centro de distribución UIO', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (3, 'Bodega BRE1', 'Centro de distribución UIO', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (51, 'Bodega BRI1', 'Centro de distribución PROV', 'activo');
INSERT INTO bodega (id_ciudad, nombre, direccion, estado) VALUES (3, 'Bodega BSA1', 'Centro de distribución UIO', 'activo');

-- ─── INVENTARIO ───
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (1, 8, 31, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (1, 5, 48, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (1, 17, 7, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (2, 14, 24, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (2, 3, 38, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (3, 18, 43, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (3, 1, 48, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (3, 4, 49, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (4, 4, 10, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (4, 10, 0, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (4, 14, 32, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (5, 10, 38, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (5, 17, 9, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (6, 18, 0, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (6, 17, 20, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (7, 4, 19, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (7, 12, 3, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (8, 3, 4, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (8, 16, 49, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (9, 16, 10, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (9, 18, 33, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (10, 7, 45, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (10, 18, 25, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (10, 20, 41, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (11, 17, 15, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (11, 15, 4, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (11, 4, 1, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (12, 19, 0, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (12, 8, 45, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (13, 8, 2, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (13, 3, 4, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (14, 9, 13, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (14, 16, 8, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (15, 8, 12, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (15, 16, 6, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (15, 14, 27, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (16, 14, 43, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (16, 15, 41, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (16, 2, 3, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (17, 4, 12, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (17, 8, 28, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (17, 7, 27, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (18, 15, 28, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (18, 8, 6, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (18, 3, 41, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (19, 3, 10, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (19, 8, 31, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (20, 13, 10, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (20, 2, 0, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (21, 15, 44, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (21, 10, 42, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (21, 14, 9, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (22, 7, 3, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (22, 2, 3, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (22, 18, 37, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (23, 2, 5, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (23, 17, 4, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (24, 8, 7, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (24, 13, 15, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (25, 20, 26, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (25, 3, 37, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (26, 9, 15, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (26, 7, 25, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (26, 11, 42, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (27, 15, 0, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (27, 11, 39, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (27, 3, 6, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (28, 17, 8, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (28, 9, 4, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (29, 10, 34, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (29, 6, 39, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (29, 15, 33, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (30, 4, 7, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (30, 5, 47, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (30, 9, 9, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (31, 20, 13, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (31, 7, 40, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (31, 11, 32, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (32, 2, 17, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (32, 3, 0, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (32, 14, 49, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (33, 6, 45, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (33, 15, 35, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (33, 18, 7, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (34, 18, 23, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (34, 2, 35, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (35, 5, 23, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (35, 2, 22, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (35, 10, 43, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (36, 12, 26, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (36, 18, 47, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (37, 6, 26, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (37, 20, 11, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (38, 8, 50, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (38, 9, 24, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (38, 6, 30, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (39, 15, 19, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (39, 12, 14, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (40, 13, 17, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (40, 11, 49, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (41, 17, 21, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (41, 13, 7, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (41, 18, 11, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (42, 2, 22, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (42, 4, 27, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (42, 14, 32, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (43, 19, 2, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (43, 7, 0, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (43, 9, 34, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (44, 12, 4, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (44, 14, 21, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (45, 4, 19, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (45, 10, 26, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (45, 17, 25, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (46, 7, 42, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (46, 14, 43, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (47, 13, 19, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (47, 18, 13, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (47, 1, 50, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (48, 15, 43, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (48, 20, 32, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (48, 19, 50, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (49, 10, 42, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (49, 17, 39, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (50, 8, 14, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (50, 10, 9, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (51, 8, 39, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (51, 16, 29, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (52, 13, 25, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (52, 16, 9, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (53, 4, 14, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (53, 14, 44, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (54, 2, 7, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (54, 18, 8, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (54, 8, 42, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (55, 15, 35, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (55, 17, 10, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (55, 14, 28, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (56, 9, 31, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (56, 17, 15, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (57, 3, 17, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (57, 10, 20, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (57, 8, 5, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (58, 8, 44, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (58, 13, 45, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (59, 14, 21, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (59, 20, 29, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (60, 7, 24, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (60, 14, 44, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (61, 16, 19, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (61, 1, 26, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (61, 12, 47, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (62, 16, 17, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (62, 8, 31, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (63, 11, 29, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (63, 13, 39, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (63, 6, 1, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (64, 3, 8, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (64, 14, 11, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (65, 13, 29, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (65, 11, 21, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (65, 7, 17, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (66, 3, 47, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (66, 16, 3, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (66, 1, 14, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (67, 2, 15, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (67, 1, 1, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (68, 8, 30, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (68, 5, 7, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (69, 15, 49, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (69, 9, 10, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (70, 6, 6, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (70, 10, 1, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (71, 13, 37, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (71, 7, 15, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (71, 3, 44, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (72, 19, 22, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (72, 2, 27, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (73, 3, 0, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (73, 17, 31, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (73, 11, 27, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (74, 5, 46, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (74, 14, 41, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (74, 6, 39, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (75, 15, 20, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (75, 14, 5, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (75, 9, 28, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (76, 19, 1, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (76, 13, 20, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (76, 11, 31, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (77, 9, 38, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (77, 11, 35, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (77, 20, 33, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (78, 8, 31, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (78, 14, 48, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (79, 16, 5, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (79, 15, 14, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (79, 1, 44, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (80, 19, 35, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (80, 12, 22, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (80, 16, 47, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (81, 12, 19, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (81, 15, 14, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (81, 9, 46, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (82, 4, 12, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (82, 18, 47, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (82, 6, 17, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (83, 4, 14, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (83, 7, 11, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (83, 10, 0, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (84, 9, 3, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (84, 2, 18, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (85, 4, 30, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (85, 1, 28, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (85, 10, 11, 5);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (86, 16, 25, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (86, 4, 4, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (86, 3, 40, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (87, 5, 36, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (87, 20, 5, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (88, 18, 38, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (88, 14, 50, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (89, 17, 28, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (89, 13, 19, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (90, 10, 39, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (90, 19, 48, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (90, 2, 40, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (91, 3, 11, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (91, 6, 4, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (91, 8, 0, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (92, 20, 2, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (92, 16, 18, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (92, 10, 44, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (93, 8, 50, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (93, 9, 37, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (94, 14, 34, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (94, 4, 41, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (95, 5, 10, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (95, 3, 38, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (95, 2, 18, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (96, 15, 44, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (96, 10, 17, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (97, 15, 27, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (97, 3, 38, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (97, 2, 1, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (98, 19, 1, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (98, 20, 17, 14);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (99, 6, 33, 15);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (99, 16, 28, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (100, 19, 40, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (100, 14, 5, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (101, 14, 42, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (101, 11, 10, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (101, 19, 26, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (102, 13, 29, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (102, 18, 20, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (102, 2, 20, 6);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (103, 17, 29, 11);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (103, 1, 3, 8);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (103, 18, 33, 10);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (104, 15, 17, 13);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (104, 2, 8, 9);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (104, 7, 28, 12);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (105, 1, 45, 7);
INSERT INTO inventario (id_producto, id_bodega, stock_actual, stock_minimo) VALUES (105, 8, 19, 13);

-- ─── CLIENTES ───
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Jorge Lenin', 'Altafuya Arias', 'altafuya-09jorge@hotmail.com', '1800627284', 'Av. Del Parque y Alonso de Torres, Quito (Envío: 3-5 días hábiles)', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (20, 'Juan Diego', 'Cabrera Vasquez', 'juandiegocab45@hotmail.com', '1800627284', 'Av. Felipe II s/n y Circunvalación Sur, Cuenca (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (34, 'Jonathan Xavier', 'Moncayo Sierra', 'jxmoncayo@gmail.com', '1800627284', 'Av.Del Periodista s/n y Dr.Juan Bautista Arzube, Guayaquil (Envío:', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (34, 'Jean', 'Carlos Jean', 'jeancarloslopex@hotmail.com', '1800627284', 'Av.Joaquín J. Orrantia s/n y Juan Tanca Marengo, Guayaquil (Envío:', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (28, 'Andrea', 'Cortes Andrea', 'namibiababy@hotmail.com', '1800627284', 'AvPedroVicenteMaldonados/nyJulioEst upiñánTello, Esmeraldas (Envío:', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (34, 'Juan', 'Guashpa Juan', 'juan.guashpa@gmail.com', '1800627284', 'Av. Colón 208 entre Av. Pichincha y Av. Pedro Carbo , Guayaquil', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Eduardo', 'Romero Eduardo', 'erockmero@hotmail.com', '1800627284', 'Av. Pedro Vicente Maldonado S11-122 y Calle Calvas, Quito', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (20, 'Andres', 'Cajamarca Andres', 'andres.cajamarca230499@gmail.com', '1800627284', 'Av. Felipe II s/n y Circunvalación Sur, Cuenca (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (45, 'Tabatha', 'López Liger', 'tabatha_lopez@hotmail.com', '1800627284', 'Av. Eloy Alfaro y Av. Benjamín Terán C, Latacunga (Envío: 3-5', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (71, 'Jose Antonio', 'Ruiz Badiola', 'tonobadiola@hotmail.com', '0939355924', 'Enríquez gallo calle 43', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Gabriel Alejandro', 'Jacome Saavedra', 'gabbojacomesaavedra@gmail.com', '1800627284', 'Av.Amazonas N16-114 y Av. de la República , Quito (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (34, 'Sonia Katharine', 'Mieles Zamora', 'kathymieleszamora@hotmail.com', '1800627284', 'Km 10.5 Vía la Aurora, Guayaquil (Envío: 3-5 días hábiles)', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (34, 'Javier', 'Plúa Rizzo', 'javier@yachtagentsgalapagos.com', '1800627284', 'Av.Joaquín J. Orrantia s/n y Juan Tanca Marengo, Guayaquil (Envío:', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (69, 'Edison David', 'Mafla Medina', 'edisonmafla2016@gmail.com', '1800627284', 'Av. Antonio José de Sucre S/N, Riobamba (Envío: 3-5 días hábiles)', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (34, 'Eduardo Andres', 'Cherrez Calle', 'echerrec2014@gmail.com', '1800627284', 'Av.Francisco de Orellana s/n y Carlos Luis Plaza Dañin,Guayaquil', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (28, 'Nadia', 'Villavicencio Nadia', 'nadiavalevillabone1505@gmail.com', '1800627284', 'AvPedroVicenteMaldonados/nyJulioEst upiñánTello, Esmeraldas (Envío:', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (76, 'Edwin Jose', 'Rojas Sancler', 'od.edwinsancler@gmail.com', '1800627284', 'Av. Quito s/n y Abraham Calazacón, Santo Domingo (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Luis', 'Flores Luis', 'luisalberto11morales@gmail.com', '1800627284', 'Av. Amazonas N36-152 y Naciones Unidas, Quito (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (36, 'Sthefy', 'Haro Sthefy', 'estefybeky5@gmail.com', '1800627284', 'Av. Mariano Acosta 2147 y Gómez Jurado, Ibarra (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Joel', 'Pinargote Sanchez', 'joelps1415@hotmail.com', '1800627284', 'Av.Amazonas N16-114 y Av. de la República , Quito (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (20, 'Nelly Cecilia', 'Calle Torres', 'nelcato15@hotmail.com', '1800627284', 'Av. Felipe II s/n y Circunvalación Sur, Cuenca (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Doris', 'Alban Doris', 'dealban.dance@gmail.com', '1800627284', 'Av. Pedro Vicente Maldonado S11-122 y Calle Calvas, Quito', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (51, 'Betsaida Marisol', 'Chimborazo Astudillo', 'betsaidamarisol96@hotmail.com', '1800627284', 'Av. Malecón s/n y  Calle 23, Manta (Envío: 3-5 días hábiles)', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (69, 'Esther Hortencia', 'Mariño Balseca', 'byron_almar@hotmail.com', '1800627284', 'Av. Antonio José de Sucre S/N, Riobamba (Envío: 3-5 días hábiles)', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (36, 'Edwin Marcelo', 'Tirado Andrade', 'mac_8300@hotmail.com', '1800627284', 'Av. Mariano Acosta 2147 y Gómez Jurado, Ibarra (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (34, 'Stéfano Jamil', 'Jimenez Reyes', 'sjjr1987@hotmail.com', '1800627284', 'Av. 25 de Julio s/n y Ernesto Albán , Guayaquil (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Consolación', 'Salazar Consolación', 'carlinsalazarfor@hotmail.com', '0995820475', 'Hualcopo', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (74, 'Eduardo Marcelo', 'Loachamin Aldaz', 'emla3720@hotmail.com', '1800627284', 'Calle Isla Santa Clara y Av. General Rumiñahui, San Rafael', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (20, 'David', 'Vazquez David', 'davidvazquez1511@gmail.com', '1800627284', 'Av. Felipe II s/n y Circunvalación Sur, Cuenca (Envío: 3-5 días', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Fabricio', 'Zambrano Fabricio', 'fgzambranob@pronaca.com', '1800627284', 'AV. Quitumbe Ñan s/n y Av. Rafael Moran Valverde, Quito (Envío: 3-5', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Wilman Fabian', 'Miranda Chango', 'wilmanelreydjpro@gmail.com', '0963631394', 'Iñaquito', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (34, 'Mishelle', 'Angulo Mishelle', 'mishelle1620@hotmail.com', '1800627284', 'Av. Francisco De orellana , Sexta Etapa Mz 2576, Guayaquil (Envío', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (43, 'William Javier', 'Quirumbay Suárez', 'williamquisu@gmail.com', '1800627284', 'Av. 12 s/n, La Libertad (Envío: 3-5 días hábiles)', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (46, 'Oscar Alfredo', 'Rosales Sarango', 'oscarrosales3@live.com', '1800627284', 'Av. 18 de Noviembre s/n y Gobernación de Mainas, Loja', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (45, 'Néstor Fernando', 'Paillacho Chicaiza', 'fernandop196@yahoo.es', '1800627284', 'Av. Eloy Alfaro y Av. Benjamín Terán C, Latacunga (Envío: 3-5', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (43, 'Oscar Alexander', 'Sabando Toledo', 'oast111106@gmail.com', '1800627284', 'Av. 12 s/n, La Libertad (Envío: 3-5 días hábiles)', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Paola', 'Vaca Paola', 'paolavaca11@hotmail.com', '1800627284', 'Av. Del Parque y Alonso de Torres, Quito (Envío: 3-5 días hábiles)', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (68, 'Belen', 'Nazate Belen', 'mabe14x@hotmail.com', '1800627284', 'AV. Quitumbe Ñan s/n y Av. Rafael Moran Valverde, Quito (Envío: 3-5', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (46, 'Marco', 'Ojeda Marco', 'abg.marcoojedac@gmail.com', '1800627284', 'Av. 18 de Noviembre s/n y Gobernación de Mainas, Loja', 'activo');
INSERT INTO cliente (id_ciudad, nombre, apellido, correo, telefono, direccion, estado) VALUES (34, 'Sebastian', 'Bajaña Sebastian', 'sebastianbajana2025@gmail.com', '1800627284', 'Av17deSeptiembreentrePresidenteEspi nozayPresidenteCarrión,Milagro', 'activo');

COMMIT;
-- ============================================================
-- SEED DATA — PARTE 2: PEDIDOS
-- Ejecutar DESPUÉS de la parte 1 y del DataInitializer
-- (necesita el usuario admin con id_usuario)
-- pedido.total lo calcula el trigger automáticamente
-- detalle_pedido.subtotal es columna GENERATED
-- ============================================================

BEGIN;

-- Verificar que existe el usuario admin (id 1 normalmente)
DO $$
DECLARE
    v_id_usuario INTEGER;
BEGIN
    SELECT id_usuario INTO v_id_usuario FROM usuario WHERE correo = 'admin@marathon.com' LIMIT 1;
    IF v_id_usuario IS NULL THEN
        RAISE EXCEPTION 'No existe el usuario admin. Arranca el backend primero para que DataInitializer lo cree.';
    END IF;
END $$;


-- ─── PEDIDOS Y DETALLES ───

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (10, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'entregado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 10, 2, 113.25);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 69, 3, 54.35);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 13, 1, 89.38);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (16, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 10.0, 'pendiente');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 55, 1, 152.3);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (15, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 10.0, 'pendiente');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 75, 2, 27.93);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (3, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'procesado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 54, 3, 38.85);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 19, 2, 109.64);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (7, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'procesado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 13, 3, 30.05);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 71, 1, 119.04);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (35, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'entregado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 60, 2, 59.75);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 75, 1, 131.84);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 59, 1, 33.1);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 47, 2, 104.03);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (29, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 10.0, 'enviado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 10, 2, 46.39);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 16, 2, 44.32);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 66, 2, 87.47);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (36, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'enviado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 89, 2, 115.1);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (5, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'pendiente');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 61, 1, 136.99);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 90, 2, 123.54);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 86, 3, 151.51);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 9, 2, 134.66);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (2, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'entregado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 22, 2, 29.43);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 79, 2, 40.69);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 15, 1, 83.66);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (6, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 5.0, 'procesado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 52, 2, 158.24);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 71, 2, 133.02);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 36, 2, 129.24);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 18, 2, 173.24);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (6, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'procesado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 30, 1, 21.93);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 85, 3, 49.17);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (1, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 5.0, 'procesado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 69, 3, 70.98);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 48, 1, 130.48);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 79, 3, 172.04);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (30, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 5.0, 'entregado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 52, 2, 36.57);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (4, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'procesado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 27, 2, 116.12);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 57, 1, 20.04);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 21, 1, 105.86);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 15, 2, 118.2);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (14, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'entregado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 82, 2, 172.87);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (31, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'pendiente');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 63, 2, 69.9);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 60, 1, 36.35);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 62, 2, 138.46);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (11, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'pendiente');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 68, 3, 166.26);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 47, 3, 67.69);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 19, 3, 158.13);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 89, 3, 155.27);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (11, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'enviado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 69, 3, 72.75);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 70, 1, 118.12);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 100, 1, 148.97);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (15, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 10.0, 'procesado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 64, 1, 146.42);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 46, 2, 61.47);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 94, 3, 116.82);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 4, 2, 91.56);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (24, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'pendiente');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 14, 1, 74.04);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 30, 2, 119.85);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 61, 3, 154.47);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (23, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'pendiente');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 50, 1, 96.49);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 101, 1, 89.43);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 92, 3, 73.2);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 97, 3, 83.33);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (6, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'procesado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 17, 2, 149.04);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 4, 1, 117.85);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 20, 3, 176.85);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 76, 3, 169.99);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (36, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'procesado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 2, 3, 123.95);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 103, 3, 139.92);

INSERT INTO pedido (id_cliente, id_usuario, descuento, estado)
VALUES (28, (SELECT id_usuario FROM usuario WHERE correo='admin@marathon.com' LIMIT 1), 0, 'procesado');
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 4, 1, 66.87);
INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (currval('pedido_id_pedido_seq'), 33, 1, 142.19);

COMMIT;

-- ============================================================
-- VERIFICACIÓN POST-CARGA
-- ============================================================
-- SELECT 'ciudades' tabla, COUNT(*) FROM ciudad
-- UNION ALL SELECT 'categorias', COUNT(*) FROM categoria
-- UNION ALL SELECT 'productos', COUNT(*) FROM producto
-- UNION ALL SELECT 'proveedores', COUNT(*) FROM proveedor
-- UNION ALL SELECT 'bodegas', COUNT(*) FROM bodega
-- UNION ALL SELECT 'inventario', COUNT(*) FROM inventario
-- UNION ALL SELECT 'clientes', COUNT(*) FROM cliente
-- UNION ALL SELECT 'pedidos', COUNT(*) FROM pedido
-- UNION ALL SELECT 'detalles', COUNT(*) FROM detalle_pedido;
