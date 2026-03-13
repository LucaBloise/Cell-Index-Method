# Cell Index Method - TP1 (Sistemas Dinamicos de Sistemas)

Implementacion del algoritmo **Cell Index Method (CIM)** para deteccion de vecinos en 2D, con comparacion contra **fuerza bruta (BF)**, soporte para condiciones periodicas y no periodicas, benchmarking y visualizacion.

## 1. Objetivo del TP

Dado un area cuadrada de lado `L` con `N` particulas de radio no nulo `ri` y radio de interaccion `rc`, se busca:

1. Detectar vecinos usando CIM (y BF para comparar).
2. Medir tiempos de ejecucion para distintos `N` y `M`.
3. Encontrar un criterio para elegir `M` optimo segun densidad.
4. Preparar salida y visualizacion para demostracion en vivo.

La distancia entre particulas se mide **borde a borde**:

\[
d_{borde}(i,j) = d_{centros}(i,j) - r_i - r_j
\]

Dos particulas son vecinas si:

\[
d_{borde}(i,j) \le rc
\]

## 2. Criterio de celdas cuando ri > 0

En particulas puntuales se usa la condicion clasica:

\[
\frac{L}{M} > rc
\]

Con radios no nulos, el peor caso centro a centro para que dos particulas sigan siendo vecinas es:

\[
d_{centros}^{max} = rc + r_i + r_j \le rc + 2r_{max}
\]

Por lo tanto, la condicion usada en este proyecto es:

\[
\frac{L}{M} \ge rc + 2r_{max}
\]

Con esto se evita perder vecinos por estar en celdas no contempladas.

Para condiciones periodicas, ademas se exige:

\[
M \ge 3
\]

para evitar doble contabilizacion en el barrido hacia adelante con wrap-around.

## 3. Que implementa este repositorio

- Generacion aleatoria de `N` particulas dentro de `[0, L) x [0, L)`.
- Deteccion de vecinos con:
	- `CIM` (Cell Index Method).
	- `BF` (Brute Force, `O(N^2)`).
- Dos modos de contorno:
	- Sin periodicas (paredes).
	- Con periodicas (toroidal, minimum-image).
- Export de salida por corrida:
	- `output.txt`: vecinos por particula.
	- `properties.txt`: parametros y tiempos.
- Benchmark de eficiencia (`N` sweep y `M` sweep), con promedio y desvio estandar.
- Benchmark adicional de grilla (`N x M`) solo para CIM, con promedio y desvio estandar.
- Visualizacion interactiva de particulas y vecinos.
- Plot de benchmark con ambos metodos y escala log automatica si corresponde.

## 4. Estructura

```text
simulation/
	Main.java
	Benchmark.java
	CIMGridBenchmark.java
	model/
		Area.java
		Particle.java

visualization/
	requirements.txt
	src/
		main.py
		benchmark_plot.py
		cim_grid_plot.py
```

## 5. Como compilar y ejecutar (Windows + PowerShell)

### 5.1 Compilar Java

```powershell
cd .\simulation
javac -d .. model\*.java Main.java Benchmark.java CIMGridBenchmark.java
```

### 5.2 Ejecutar simulacion puntual (Main)

Sintaxis:

```powershell
java -cp .. simulation.Main <N> <L> <M> <rc> <method> <periodic> <r>
java -cp .. simulation.Main <N> <L> <M> <rc> <method> <periodic> <rMin> <rMax>
```

Donde:

- `method`: `CIM` o `BF`
- `periodic`: `true` o `false`

Ejemplo:

```powershell
java -cp .. simulation.Main 500 20 10 1 CIM false 0.23 0.26
```

Salida generada en:

```text
simulation/outputs/<timestamp>/
	output.txt
	properties.txt
```

### 5.3 Ejecutar benchmark (punto 2)

```powershell
java -cp .. simulation.Benchmark
```

Genera:

```text
simulation/outputs/benchmark/results.csv
```

Incluye dos estudios:

- `N_sweep`: varia `N`, con `M` fijo.
- `M_sweep`: varia `M`, con `N` fijo.

Para cada configuracion se hace warm-up y luego multiples corridas para calcular:

- tiempo promedio (ms)
- desvio estandar muestral (ms)

### 5.4 Ejecutar benchmark de grilla CIM (N x M)

Este benchmark implementa el barrido pedido para estudiar eficiencia de CIM en funcion de `N` y `M`.

```powershell
java -cp .. simulation.CIMGridBenchmark
```

Genera:

```text
simulation/outputs/benchmark/cim_grid_results.csv
```

Configuracion actual del barrido:

- Parametros fijos: `L=20`, `rc=1`, `ri ~ U[0.23, 0.26]`, `periodic=false`.
- `N`: `100, 300, 500, 800, 1000`.
- `M`: `3, 5, 7, 9, 11, 13`.
- Por cada combinacion `(N, M)`:
	- `K_WARMUP = 3` corridas de calentamiento (descartadas).
	- `K_RUNS = 15` corridas medidas.

En el CSV:

- `mean_ms`: promedio sobre las 15 corridas medidas.
- `std_ms`: desvio estandar muestral sobre esas 15 corridas.
- `status`: `ok`, `invalid` o `failed`.

## 6. Visualizacion (punto 1 y 6)

### 6.1 Instalar dependencias de Python

Desde `visualization/`:

```powershell
pip install -r requirements.txt
```

### 6.2 Visualizar una corrida de simulacion

Desde `visualization/src/`:

```powershell
python main.py
```

o para una corrida puntual:

```powershell
python main.py ..\..\simulation\outputs\<timestamp>
```

Interaccion:

- click en una particula: seleccionada en rojo
- vecinos en verde
- click nuevamente o en fondo: deseleccion
- checkbox para mostrar/ocultar grilla

### 6.3 Graficar benchmark

Desde `visualization/src/`:

```powershell
python benchmark_plot.py
```

Muestra CIM y BF en la misma figura, con barras de error y escala log automatica cuando hay diferencias de ordenes de magnitud.

### 6.4 Graficar benchmark de grilla CIM (solo M en eje X)

Desde `visualization/src/`:

```powershell
python cim_grid_plot.py
```

Genera una unica figura con:

- eje X: `M`
- eje Y: tiempo de ejecucion (ms)
- una curva por cada `N`
- barras de error usando `std_ms` (desvio estandar)

## 7. Formato de I/O utilizado

### Input (en esta implementacion)

Para este TP se genera una configuracion aleatoria para un unico instante `t0`, usando los parametros por linea de comando (`N, L, M, rc, radios, metodo, contorno`).

### Output

`output.txt`:

```text
id\tx\ty\tr\t<id_vecino_1> <id_vecino_2> ...
```

`properties.txt` incluye:

- parametros (`N, L, M, rc, radios`)
- metodo (`CIM` o `BF`)
- tipo de contorno
- tiempo de ejecucion
- cantidad de pares vecinos encontrados

## 8. Criterio de M optimo (punto 3)

### Criterio teorico

Para no perder vecinos:

\[
M \le \left\lfloor \frac{L}{rc + 2r_{max}} \right\rfloor
\]

Y conviene tomar el mayor `M` valido porque reduce la cantidad de particulas por celda y, en promedio, disminuye comparaciones.

### Para este TP

Con `L = 20`, `rc = 1`, `r \in [0.23, 0.26]`:

\[
M_{max} = \left\lfloor \frac{20}{1 + 2(0.26)} \right\rfloor = \lfloor 13.157... \rfloor = 13
\]

Por benchmark, el optimo practico para CIM queda cerca del maximo permitido (tipicamente `M = 13`, sujeto a ruido estadistico y plataforma).

## 9. Estado de cumplimiento del enunciado

- Punto 1: implementado (CIM + BF, periodico/no periodico, tiempos, salida de vecinos, visualizacion).
- Punto 2: implementado (benchmark vs `N` y `M`, promedio/desvio, comparacion CIM vs BF, grafico).
- Punto 3: implementado criterio teorico + verificacion empirica.
- Punto 4: listo para demo en vivo con parametros arbitrarios.
- Punto 5/6: formato de salida y visualizacion cubiertos.

## 10. Nota para la demo en clase

Flujo sugerido rapido:

1. Compilar (`javac ...`).
2. Correr `Main` con parametros pedidos en clase.
3. Mostrar `output.txt`/`properties.txt`.
4. Visualizar particula y vecinos con `python main.py`.
5. Correr `Benchmark` y graficar `python benchmark_plot.py`.
6. Correr `CIMGridBenchmark` y graficar `python cim_grid_plot.py`.
7. Variar `M` y contrastar con el criterio de `M` optimo.