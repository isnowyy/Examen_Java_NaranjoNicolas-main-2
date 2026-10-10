# CrediYa S.A.S. - Sistema de cobros de cartera

Aplicación de consola en Java para manejar los préstamos de CrediYa: empleados, clientes,
préstamos, pagos y reportes. Los datos se pueden guardar en MySQL (JDBC), en archivos de
texto o solo en memoria.

La idea es reemplazar las hojas de cálculo que usaba la empresa. El sistema valida los datos
al ingresarlos, calcula los intereses, no deja abonar más de lo que se debe y saca los
reportes de mora con la información real.

## Contenido

- [Parte del examen](#parte-del-examen)
- [Módulos](#módulos)
- [Cómo ejecutarlo](#cómo-ejecutarlo)
- [Base de datos](#base-de-datos)
- [Estructura](#estructura)
- [Diagramas](#diagramas)
- [Diseño](#diseño)
- [Ejemplos](#ejemplos)
- [Pruebas](#pruebas)
- [Limitaciones](#limitaciones)

## Parte del examen

Lo que pide el enunciado está en el paquete `com.crediya.gestion`:

| Punto | Archivo |
|---|---|
| 1. Clase `Prestamo` (atributos, constructores, getters/setters, `calcularTotalAPagar()`, `toString()`) | [`Prestamo.java`](src/main/java/com/crediya/gestion/Prestamo.java) |
| 2. `GestorPrestamos` con `ArrayList`, `registrarPrestamo()` y `listarPrestamos()`; lanza `DatoInvalidoException` si monto o plazo no son mayores que 0 | [`GestorPrestamos.java`](src/main/java/com/crediya/gestion/GestorPrestamos.java), [`DatoInvalidoException.java`](src/main/java/com/crediya/gestion/DatoInvalidoException.java) |
| 3. `guardarPrestamoEnBD()` con `ConexionBD` (Singleton) | `GestorPrestamos.guardarPrestamoEnBD` |
| 4. `buscarPorEstado()` con Stream API y lambdas | `GestorPrestamos.buscarPorEstado` |
| Script SQL | [`sql/crediya_db.sql`](sql/crediya_db.sql) |
| Main de prueba | [`MainGestionPrestamos.java`](src/main/java/com/crediya/gestion/MainGestionPrestamos.java) |

Para correrlo:

```bash
./run.sh gestion
```

o sin el script:

```bash
mvn compile
mvn exec:java -Dexec.mainClass=com.crediya.gestion.MainGestionPrestamos
```

Todo corre en memoria menos la última parte, que guarda en MySQL. Para esa parte hay que
crear la base primero (ver [Base de datos](#base-de-datos)).

Salida:

```
=== REGISTRO DE PRÉSTAMOS ===
Préstamo registrado correctamente.
Préstamo registrado correctamente.
Préstamo registrado correctamente.
Error: El monto debe ser mayor que 0 (se recibió 0.0).
Error: El plazo debe ser mayor que 0 meses (se recibió 0).
Error: El estado 'Cancelado' no es válido. Valores posibles: Activo, Pagado, Mora.

=== LISTADO DE PRÉSTAMOS ===
ID: 1 - Cliente: 5 - Monto: 500000.0 - Interés: 2.5% - Plazo: 6 meses - Total: 575000.0
ID: 2 - Cliente: 2 - Monto: 2000000.0 - Interés: 3.0% - Plazo: 12 meses - Total: 2720000.0
ID: 3 - Cliente: 3 - Monto: 1200000.0 - Interés: 2.0% - Plazo: 4 meses - Total: 1296000.0

=== PRÉSTAMOS EN ESTADO 'Activo' ===
Préstamo ID 1 - Estado: Activo

=== PRÉSTAMOS EN ESTADO 'Mora' ===
Préstamo ID 2 - Estado: Mora

=== GUARDADO EN MySQL (tabla prestamos) ===
Préstamo ID 1 guardado en la base de datos con ID 7.
Préstamo ID 2 guardado en la base de datos con ID 8.
Préstamo ID 3 no se guardó: Un préstamo nuevo no puede guardarse como Pagado: pasa a ese estado cuando sus abonos cubren el total, y todavía no tiene ninguno.
```

Los ID 7 y 8 salen así porque el script SQL ya trae 6 préstamos de ejemplo. Si MySQL no
está prendido, la parte en memoria funciona igual y al final sale un aviso de que no se pudo
conectar (el detalle queda en `datos/bitacora.log`).

### Notas sobre la solución

- `calcularTotalAPagar()` usa la fórmula del enunciado:
  `monto + (monto * interesMensual / 100 * plazoMeses)`. Con 500.000 al 2,5 % por 6 meses da
  575.000. Es la misma fórmula de `InteresSimple`, así que el resto del sistema calcula el
  mismo total cuando lee estos préstamos de la base.
- Los atributos son privados. `setEstado` solo acepta `Activo`, `Pagado` o `Mora` (sin
  importar mayúsculas); cualquier otro valor lanza `DatoInvalidoException`.
- Monto y plazo se validan en `registrarPrestamo` y no en el constructor, porque ahí es donde
  lo pide el enunciado. También se valida que el plazo no pase de 120 meses, que el interés
  esté entre 0 y 100 y que el ID no esté repetido (con ID 0 se asigna el siguiente).
- `DatoInvalidoException` extiende `Exception`, o sea que es verificada y obliga a usar
  `try/catch`. Las demás excepciones del proyecto son `RuntimeException`.
- `buscarPorEstado` filtra con `stream().filter(...)`. Si el estado no existe (por ejemplo
  `"Cancelado"`) lanza la excepción en vez de devolver una lista vacía.
- `guardarPrestamoEnBD` usa `PreparedStatement` y try-with-resources, y devuelve el ID que
  genera MySQL. No deja guardar un préstamo nuevo como `Pagado`, porque en el sistema un
  préstamo queda pagado cuando sus abonos cubren el total y uno nuevo no tiene abonos.
- El enunciado pide `double`. Antes de insertar, monto e interés se pasan a `BigDecimal` con
  dos decimales.

### Cambios para que funcione con el resto del sistema

El gestor guarda en la misma tabla `prestamos` que usa la aplicación principal, entonces hubo
que ajustar algunas cosas:

- Las columnas `interes` y `cuotas` ahora se llaman `interes_mensual` y `plazo_meses`, como
  en el script base del examen.
- Los estados pasaron de `PENDIENTE`/`PAGADO` a `Activo`/`Pagado`/`Mora`. Los archivos viejos
  con `PENDIENTE` se siguen leyendo como `Activo`.
- `empleado_id` puede ser `NULL` porque los préstamos del examen no tienen asesor. En el
  detalle salen como "Sin asignar".
- Un préstamo en `Mora` cuenta como vencido en los reportes y bloquea créditos nuevos para
  ese cliente.

Si ya tenía la base creada de antes, hay que volver a correr `sql/crediya_db.sql`. Si no, el
guardado falla con `Unknown column 'interes_mensual'`.

## Módulos

- **Empleados**: registrar, listar, buscar por documento, filtrar por rol, cambiar salario,
  ver nómina, eliminar.
- **Clientes**: registrar, listar, buscar por documento o nombre, ver la cartera de un
  cliente, eliminar.
- **Préstamos**: simular un crédito, otorgarlo, ver el detalle con el plan de pagos, cambiar
  el estado, ver lo colocado por cada empleado, eliminar.
- **Pagos**: registrar abonos, ver el historial de un préstamo, ver el recaudo por mes,
  eliminar un abono.
- **Reportes**: resumen de cartera, préstamos activos y vencidos, clientes morosos, mayores
  deudores, comisiones por empleado, filtro por monto y exportación a `.txt`.

El total y la cuota no se escriben a mano, el sistema los calcula según la modalidad: interés
simple, compuesto o cuota fija (sistema francés).

## Cómo ejecutarlo

Se necesita JDK 17+ y Maven 3.8+. MySQL 8 solo si se va a usar la base de datos.

```bash
git clone https://github.com/isnowyy/Examen_Java_NaranjoNicolas.git
cd Examen_Java_NaranjoNicolas
./run.sh
```

Opciones de `run.sh`:

```bash
./run.sh memoria    # sin base de datos ni archivos
./run.sh archivo    # usa los .txt de datos/
./run.sh mysql      # usa MySQL
./run.sh gestion    # main del examen
```

También se puede generar un jar con el driver incluido:

```bash
mvn package
java -jar target/crediya-jar-with-dependencies.jar
```

Si no se puede conectar a MySQL, el programa avisa y pregunta si quiere seguir con archivos
de texto.

## Base de datos

Crear la base con los datos de ejemplo:

```bash
mysql -u root -p < sql/crediya_db.sql
```

La aplicación no usa `root`, usa el usuario `crediya_app`. El archivo
`sql/crear_usuario.sql` tiene `CAMBIE_ESTA_CLAVE` en lugar de la clave, así que hay que
reemplazarla al ejecutarlo (sin editar el archivo):

```bash
sed "s/CAMBIE_ESTA_CLAVE/mi_clave/" sql/crear_usuario.sql | mysql -u root -p
echo "crediya.db.clave=mi_clave" > crediya.local.properties
```

`crediya.local.properties` está en el `.gitignore`. El resto de la configuración (URL,
usuario) ya está en `src/main/resources/crediya.properties`. También se puede pasar la clave
con la variable de entorno `CREDIYA_DB_CLAVE`.

### Cambios al script base

La tabla `prestamos` mantiene las columnas del script del examen. Lo que se agregó:

- `tipo_interes` (por defecto `SIMPLE`), para saber con qué fórmula se calculó cada préstamo.
- `empleado_id` (opcional), el asesor que hizo el préstamo.
- `fecha_inicio` (por defecto la fecha actual), para calcular el vencimiento. Necesita MySQL
  8.0.13 o superior.
- `NOT NULL` y `UNIQUE` en documentos y correos.
- `CHECK` en montos, tasas, plazos y estados.
- Llaves foráneas: no se puede borrar un cliente con préstamos, y al borrar un préstamo se
  borran sus pagos.
- Índices en `cliente_id`, `empleado_id` y `estado`.
- La vista `v_saldo_prestamos` para ver los saldos desde SQL.

## Estructura

```
├── pom.xml
├── run.sh
├── sql/                 script de la base y del usuario
├── datos/               archivos .txt
├── docs/                diagramas (.mmd y PNG)
└── src/
    ├── main/java/com/crediya/
    │   ├── app/         Main y menús
    │   ├── gestion/     parte del examen
    │   ├── modelo/      Persona, Empleado, Cliente, Prestamo, Pago, cálculo de intereses
    │   ├── repositorio/ interfaces + implementaciones jdbc, archivo y memoria
    │   ├── servicio/    lógica de negocio y reportes
    │   ├── config/      configuración
    │   ├── excepcion/   excepciones propias
    │   └── util/        Consola, Dinero, Validaciones, Bitacora
    └── test/java/com/crediya/
```

## Diagramas

Clases del dominio:

![Diagrama de clases](docs/img/diagrama-clases.png)

Capas y repositorios:

![Diagrama de arquitectura](docs/img/diagrama-arquitectura.png)

Gestor de préstamos del examen:

![Diagrama del gestor de préstamos](docs/img/diagrama-gestion.png)

Los archivos fuente están en `docs/*.mmd` (Mermaid).

## Diseño

- El dinero se maneja con `BigDecimal` (clase `util/Dinero`, 2 decimales, redondeo
  `HALF_UP`) para no tener errores de redondeo. La excepción es el `Prestamo` del examen, que
  usa `double` porque lo pide el enunciado.
- `Prestamo` controla sus propios pagos: no acepta abonos mayores al saldo y pasa a `Pagado`
  solo cuando el saldo llega a 0. Si se elimina un abono vuelve a `Activo`.
- "Vencido" no se guarda, se calcula: un préstamo está vencido si tiene saldo y ya pasó su
  fecha o está en `Mora`.
- Los servicios reciben las interfaces de repositorio por constructor, por eso las pruebas
  pueden usar los repositorios en memoria.
- Para eliminar hay reglas: no se borra un cliente o empleado con préstamos, ni un préstamo
  que ya tiene abonos. Estas reglas están en los servicios para que funcionen igual con
  MySQL, archivos o memoria.

Patrones usados:

- **Repository**: `Repositorio<T>` con implementaciones JDBC, archivo y memoria.
- **Template Method**: `RepositorioArchivo`, `RepositorioJdbc` y `Menu`.
- **Strategy**: `CalculadoraInteres` (`InteresSimple`, `InteresCompuesto`, `CuotaFija`).
- **Factory**: `CalculadoraInteresFactory` y `FabricaRepositorios`.
- **Singleton**: `ConexionBD` y `Configuracion`.
- **Builder**: `Prestamo.Builder`.
- **DTO**: los records de `servicio/reporte`.

Excepciones:

```
CrediYaException (RuntimeException)
├── ValidacionException
├── ReglaNegocioException
├── RecursoNoEncontradoException
└── PersistenciaException

DatoInvalidoException (Exception)   -> la del examen
```

Los errores se atrapan en `Menu.ejecutarConRed()`: se muestra un mensaje, la traza se guarda
en `datos/bitacora.log` y el programa sigue.

## Ejemplos

Simulación de un crédito:

```
-- Simulacion de credito
  Monto a prestar: 5000000
  Interes mensual (%): 2
  Numero de cuotas: 12

    1) SIMPLE         Interes simple sobre el capital
    2) COMPUESTO      Interes compuesto capitalizado mes a mes
    3) CUOTA_FIJA     Amortizacion con cuota fija (sistema frances)
  Modalidad: 1

  Capital      : $ 5.000.000,00
  Monto total  : $ 6.200.000,00
  Intereses    : $ 1.200.000,00
  Cuota mensual: $ 516.666,67  x 12 meses
  [i] Simulacion informativa: no quedo nada registrado.
```

Cliente en mora que pide otro crédito:

```
  [!] Marta Quintero tiene 1 prestamo(s) vencido(s); el mas antiguo lleva 92 dias
      de mora. No se puede otorgar credito nuevo.
```

Intentar borrar un cliente con préstamos:

```
  Confirma eliminar a #1 Pedro Sanchez Diaz (doc. 1005432198) - Cliente, tel. 3105558877? (s/n): s
  [!] No se puede eliminar a Pedro Sanchez Diaz: tiene 2 prestamo(s) registrado(s).
      Elimine primero sus prestamos.
```

Resumen de cartera:

```
-- Resumen de cartera al 2026-10-01
Prestamos registrados : 6
  activos             : 5
  pagados             : 1
  vencidos            : 2
Capital colocado      : $ 31.500.000,00
Saldo por cobrar      : $ 33.280.151,87
Total recaudado       : $ 5.844.666,67
Indice de mora        : 40.00 %
```

Los archivos de texto usan `;` como separador y la primera línea tiene los nombres de las
columnas:

```
# id;nombre;documento;rol;correo;salario
1;Ana Maria Gomez;1098765432;ASESOR;ana.gomez@crediya.co;3200000.00
2;Carlos Pena Ruiz;91234567;COBRADOR;carlos.pena@crediya.co;2400000.00
```

## Pruebas

```bash
mvn test
```

Son 119 pruebas con JUnit 5. Cubren las fórmulas de interés, los pagos y el cambio de estado
de los préstamos, las reglas para negar créditos y para eliminar, los reportes, la lectura y
escritura de archivos, las validaciones y la parte del examen (`gestion.PrestamoTest` y
`GestorPrestamosTest`).

Ninguna prueba necesita MySQL: usan los repositorios en memoria o una carpeta temporal. El
guardado en la base se probó a mano con MySQL 8.

## Limitaciones

- Registrar un pago y actualizar el préstamo son dos escrituras separadas, sin transacción.
- Los repositorios de archivo reescriben el archivo completo en cada operación.
- No hay pool de conexiones.
- La lista del `GestorPrestamos` solo vive en memoria: guarda en la base pero no lee de ella.
- No hay login de usuarios.

## Licencia

Proyecto académico.
