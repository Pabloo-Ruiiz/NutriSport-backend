package com.nutrisport.catalogo.servicio;

import com.nutrisport.catalogo.dto.ResultadoImportacion;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.PorcionCasera;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.perfil.entidad.Alergeno;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importa alimentos públicos desde un CSV (§3.I: importación por lotes del administrador; también los
 * datos de desarrollo). Formato: UTF-8, separador {@code ;}, primera línea con la cabecera
 * {@link #COLUMNAS} (en cualquier orden), decimales con punto o coma, booleanos {@code true}/{@code false},
 * porciones {@code "unidad mediana:120|taza:200"} y alérgenos con los códigos de {@link Alergeno}
 * separados por {@code |}. Se admiten campos entre comillas dobles, pero no saltos de línea dentro de
 * un campo.
 * <p>
 * Cada fila se valida por separado: las que tienen errores se informan en el resultado y no detienen
 * la importación. Las válidas se crean o, si ya hay un alimento público con el mismo nombre, marca y
 * preparación, se sobrescriben. Todo ocurre en una transacción.
 */
@Service
public class ImportadorAlimentosCsv {

    static final int MAXIMO_FILAS = 5000;

    static final List<String> COLUMNAS = List.of("nombre", "marca", "categoria", "preparacion", "kcal100",
            "proteinas100", "carbohidratos100", "azucares100", "grasas100", "saturadas100", "fibra100", "sal100",
            "unidad_base", "porciones", "alergenos_contiene", "alergenos_trazas", "contiene_lactosa", "apto_vegano",
            "apto_vegetariano", "apto_pescetariano");

    private static final BigDecimal MAXIMO_KCAL = new BigDecimal("900");
    private static final BigDecimal MAXIMO_NUTRIENTE = new BigDecimal("100");
    private static final BigDecimal MAXIMO_GRAMOS_PORCION = new BigDecimal("5000");
    private static final int DECIMALES = 2;

    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;

    public ImportadorAlimentosCsv(AlimentoRepositorio alimentoRepositorio, CategoriaRepositorio categoriaRepositorio) {
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
    }

    /**
     * Importa el CSV como alimentos públicos sin validar.
     *
     * @param fuente             procedencia de los datos (trazabilidad)
     * @param referencia         referencia externa común a todas las filas (p. ej. "DEV"); puede ser nula
     * @param alergenosRevisados si los alérgenos del fichero se dan por revisados por un administrador
     * @throws PeticionInvalidaException si falta la cabecera o alguna columna, o hay más de 5000 filas
     */
    @Transactional
    public ResultadoImportacion importar(InputStream entrada, FuenteDato fuente, String referencia,
            boolean alergenosRevisados) {
        List<LineaCsv> lineas = leer(entrada);
        if (lineas.isEmpty()) {
            throw new PeticionInvalidaException("El fichero está vacío");
        }
        Map<String, Integer> indices = indicesCabecera(lineas.get(0));
        Map<String, Categoria> categorias = categoriaRepositorio.findByTipoOrderByOrden(TipoCategoria.ALIMENTO)
                .stream().collect(Collectors.toMap(Categoria::getNombre, Function.identity()));

        int creados = 0;
        int actualizados = 0;
        List<ErrorCampo> errores = new ArrayList<>();
        for (LineaCsv linea : lineas.subList(1, lineas.size())) {
            Fila fila = new Fila(linea.campos(), indices);
            DatosAlimento datos = fila.datos(categorias);
            if (!fila.errores.isEmpty()) {
                errores.add(new ErrorCampo("fila " + linea.numero(), String.join("; ", fila.errores)));
                continue;
            }
            if (guardar(datos, fuente, referencia, alergenosRevisados)) {
                creados++;
            } else {
                actualizados++;
            }
        }
        return new ResultadoImportacion(creados, actualizados, errores);
    }

    // ---------------------------------------------------------------------------------------------
    // Lectura
    // ---------------------------------------------------------------------------------------------

    private record LineaCsv(int numero, List<String> campos) {
    }

    /** Lee las líneas no vacías con su número en el fichero; corta al pasar del máximo de filas. */
    private static List<LineaCsv> leer(InputStream entrada) {
        List<LineaCsv> lineas = new ArrayList<>();
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            String texto;
            int numero = 0;
            while ((texto = lector.readLine()) != null) {
                numero++;
                if (numero == 1 && texto.startsWith("﻿")) {
                    texto = texto.substring(1);
                }
                if (texto.isBlank()) {
                    continue;
                }
                // La primera línea no vacía es la cabecera
                if (lineas.size() > MAXIMO_FILAS) {
                    throw new PeticionInvalidaException(
                            "El fichero supera el máximo de " + MAXIMO_FILAS + " filas de alimentos");
                }
                lineas.add(new LineaCsv(numero, dividir(texto)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido leer el fichero CSV", e);
        }
        return lineas;
    }

    /** Divide una línea por {@code ;} respetando los campos entre comillas dobles ({@code ""} = comilla). */
    static List<String> dividir(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (entreComillas) {
                if (c == '"' && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else if (c == '"') {
                    entreComillas = false;
                } else {
                    actual.append(c);
                }
            } else if (c == '"' && actual.isEmpty()) {
                entreComillas = true;
            } else if (c == ';') {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString());
        return campos;
    }

    private static Map<String, Integer> indicesCabecera(LineaCsv cabecera) {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < cabecera.campos().size(); i++) {
            indices.put(cabecera.campos().get(i).strip().toLowerCase(Locale.ROOT), i);
        }
        List<String> faltan = COLUMNAS.stream().filter(columna -> !indices.containsKey(columna)).toList();
        if (!faltan.isEmpty()) {
            throw new PeticionInvalidaException("La cabecera del CSV no es válida", List.of(new ErrorCampo(
                    "fila " + cabecera.numero(), "Faltan las columnas: " + String.join(", ", faltan))));
        }
        return indices;
    }

    // ---------------------------------------------------------------------------------------------
    // Validación de una fila
    // ---------------------------------------------------------------------------------------------

    /** Datos ya validados de una fila. */
    private record DatosAlimento(String nombre, String marca, Categoria categoria, Preparacion preparacion,
            BigDecimal kcal100, BigDecimal proteinas100, BigDecimal carbohidratos100, BigDecimal azucares100,
            BigDecimal grasas100, BigDecimal saturadas100, BigDecimal fibra100, BigDecimal sal100,
            UnidadBase unidadBase, List<PorcionCasera> porciones, List<String> alergenosContiene,
            List<String> alergenosTrazas, boolean contieneLactosa, boolean aptoVegano, boolean aptoVegetariano,
            boolean aptoPescetariano) {
    }

    /** Lee y valida los campos de una fila acumulando todos los errores, no solo el primero. */
    private static final class Fila {

        private final List<String> campos;
        private final Map<String, Integer> indices;
        private final List<String> errores = new ArrayList<>();

        Fila(List<String> campos, Map<String, Integer> indices) {
            this.campos = campos;
            this.indices = indices;
        }

        DatosAlimento datos(Map<String, Categoria> categorias) {
            int columnasNecesarias = COLUMNAS.stream().mapToInt(indices::get).max().orElse(0) + 1;
            if (campos.size() < columnasNecesarias) {
                errores.add("tiene " + campos.size() + " columnas y hacen falta " + columnasNecesarias);
                return null;
            }
            String nombre = texto("nombre", true, 150);
            String marca = texto("marca", false, 80);
            String nombreCategoria = texto("categoria", true, 80);
            Categoria categoria = null;
            if (nombreCategoria != null) {
                categoria = categorias.get(nombreCategoria);
                if (categoria == null) {
                    errores.add("categoria: no existe la categoría de alimentos '" + nombreCategoria + "'");
                }
            }
            Preparacion preparacion = enumerado("preparacion", Preparacion.class, false);
            BigDecimal kcal = decimal("kcal100", true, MAXIMO_KCAL);
            BigDecimal proteinas = decimal("proteinas100", true, MAXIMO_NUTRIENTE);
            BigDecimal carbohidratos = decimal("carbohidratos100", true, MAXIMO_NUTRIENTE);
            BigDecimal azucares = decimal("azucares100", false, MAXIMO_NUTRIENTE);
            BigDecimal grasas = decimal("grasas100", true, MAXIMO_NUTRIENTE);
            BigDecimal saturadas = decimal("saturadas100", false, MAXIMO_NUTRIENTE);
            BigDecimal fibra = decimal("fibra100", false, MAXIMO_NUTRIENTE);
            BigDecimal sal = decimal("sal100", false, MAXIMO_NUTRIENTE);
            UnidadBase unidadBase = enumerado("unidad_base", UnidadBase.class, true);
            List<PorcionCasera> porciones = porciones();
            List<String> contiene = alergenos("alergenos_contiene");
            List<String> trazas = alergenos("alergenos_trazas");
            Boolean lactosa = booleano("contiene_lactosa");
            Boolean vegano = booleano("apto_vegano");
            Boolean vegetariano = booleano("apto_vegetariano");
            Boolean pescetariano = booleano("apto_pescetariano");

            if (Boolean.TRUE.equals(vegano) && Boolean.FALSE.equals(vegetariano)) {
                errores.add("apto_vegetariano: un alimento vegano también es vegetariano");
            }
            if (Boolean.TRUE.equals(vegetariano) && Boolean.FALSE.equals(pescetariano)) {
                errores.add("apto_pescetariano: un alimento vegetariano también es pescetariano");
            }
            if (!errores.isEmpty()) {
                return null;
            }
            return new DatosAlimento(nombre, marca, categoria, preparacion == null ? Preparacion.OTRA : preparacion,
                    kcal, proteinas, carbohidratos, azucares, grasas, saturadas, fibra, sal, unidadBase, porciones,
                    contiene, trazas, lactosa, vegano, vegetariano, pescetariano);
        }

        /** Valor recortado de la columna, o {@code null} si está vacío. */
        private String valor(String columna) {
            String valor = campos.get(indices.get(columna)).strip();
            return valor.isEmpty() ? null : valor;
        }

        private String texto(String columna, boolean obligatorio, int longitudMaxima) {
            String valor = valor(columna);
            if (valor == null && obligatorio) {
                errores.add(columna + ": es obligatorio");
            } else if (valor != null && valor.length() > longitudMaxima) {
                errores.add(columna + ": no puede superar los " + longitudMaxima + " caracteres");
            }
            return valor;
        }

        private BigDecimal decimal(String columna, boolean obligatorio, BigDecimal maximo) {
            String valor = valor(columna);
            if (valor == null) {
                if (obligatorio) {
                    errores.add(columna + ": es obligatorio");
                }
                return null;
            }
            try {
                BigDecimal numero = new BigDecimal(valor.replace(',', '.'));
                if (numero.signum() < 0 || numero.compareTo(maximo) > 0) {
                    errores.add(columna + ": debe estar entre 0 y " + maximo.toPlainString());
                } else if (numero.stripTrailingZeros().scale() > DECIMALES) {
                    errores.add(columna + ": admite como mucho " + DECIMALES + " decimales");
                }
                return numero;
            } catch (NumberFormatException e) {
                errores.add(columna + ": '" + valor + "' no es un número");
                return null;
            }
        }

        private <E extends Enum<E>> E enumerado(String columna, Class<E> tipo, boolean obligatorio) {
            String valor = valor(columna);
            if (valor == null) {
                if (obligatorio) {
                    errores.add(columna + ": es obligatorio");
                }
                return null;
            }
            try {
                return Enum.valueOf(tipo, valor.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errores.add(columna + ": '" + valor + "' no es un valor válido");
                return null;
            }
        }

        private Boolean booleano(String columna) {
            String valor = valor(columna);
            if ("true".equalsIgnoreCase(valor)) {
                return true;
            }
            if ("false".equalsIgnoreCase(valor)) {
                return false;
            }
            errores.add(columna + ": debe ser true o false");
            return null;
        }

        private List<String> alergenos(String columna) {
            String valor = valor(columna);
            Set<String> codigos = new LinkedHashSet<>();
            if (valor != null) {
                for (String codigo : valor.split("\\|")) {
                    String limpio = codigo.strip().toUpperCase(Locale.ROOT);
                    if (limpio.isEmpty()) {
                        continue;
                    }
                    try {
                        codigos.add(Alergeno.valueOf(limpio).name());
                    } catch (IllegalArgumentException e) {
                        errores.add(columna + ": '" + codigo.strip() + "' no es un alérgeno válido");
                    }
                }
            }
            return List.copyOf(codigos);
        }

        private List<PorcionCasera> porciones() {
            String valor = valor("porciones");
            List<PorcionCasera> porciones = new ArrayList<>();
            if (valor == null) {
                return porciones;
            }
            for (String texto : valor.split("\\|")) {
                int separador = texto.lastIndexOf(':');
                String nombre = separador > 0 ? texto.substring(0, separador).strip() : "";
                try {
                    BigDecimal gramos = new BigDecimal(texto.substring(separador + 1).strip().replace(',', '.'));
                    if (nombre.isEmpty() || nombre.length() > 40 || gramos.signum() <= 0
                            || gramos.compareTo(MAXIMO_GRAMOS_PORCION) > 0) {
                        throw new NumberFormatException();
                    }
                    porciones.add(new PorcionCasera(nombre, gramos));
                } catch (NumberFormatException e) {
                    errores.add("porciones: '" + texto.strip() + "' debe tener el formato nombre:gramos "
                            + "(nombre de hasta 40 caracteres y gramos entre 0 y 5000)");
                }
            }
            return porciones;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Guardado
    // ---------------------------------------------------------------------------------------------

    /** Crea o sobrescribe el alimento público; devuelve {@code true} si es nuevo. */
    private boolean guardar(DatosAlimento datos, FuenteDato fuente, String referencia, boolean alergenosRevisados) {
        Alimento alimento = alimentoRepositorio.buscarPublico(datos.nombre(), datos.marca(), datos.preparacion())
                .orElse(null);
        boolean nuevo = alimento == null;
        if (nuevo) {
            alimento = new Alimento();
        }
        alimento.setNombre(datos.nombre());
        alimento.setMarca(datos.marca());
        alimento.setCategoria(datos.categoria());
        alimento.setPreparacion(datos.preparacion());
        alimento.setKcal100(datos.kcal100());
        alimento.setProteinas100(datos.proteinas100());
        alimento.setCarbohidratos100(datos.carbohidratos100());
        alimento.setAzucares100(datos.azucares100());
        alimento.setGrasas100(datos.grasas100());
        alimento.setSaturadas100(datos.saturadas100());
        alimento.setFibra100(datos.fibra100());
        alimento.setSal100(datos.sal100());
        alimento.setUnidadBase(datos.unidadBase());
        alimento.setPorciones(datos.porciones());
        alimento.setAlergenosContiene(datos.alergenosContiene());
        alimento.setAlergenosTrazas(datos.alergenosTrazas());
        alimento.setContieneLactosa(datos.contieneLactosa());
        alimento.setAptoVegano(datos.aptoVegano());
        alimento.setAptoVegetariano(datos.aptoVegetariano());
        alimento.setAptoPescetariano(datos.aptoPescetariano());
        alimento.setFuente(fuente);
        alimento.setReferenciaExterna(referencia);
        alimento.setAlergenosRevisados(alergenosRevisados);
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimento.setPropietario(null);
        alimento.setValidado(false);
        // activo no se toca al actualizar: si un administrador desactivó el alimento, sigue desactivado
        alimentoRepositorio.save(alimento);
        return nuevo;
    }
}
