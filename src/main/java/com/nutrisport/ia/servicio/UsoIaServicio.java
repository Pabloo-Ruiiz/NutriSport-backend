package com.nutrisport.ia.servicio;

import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.comun.config.IaProperties;
import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.entidad.UsoIa;
import com.nutrisport.ia.puerto.ResultadoIa;
import com.nutrisport.ia.repositorio.UsoIaRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra cada llamada al proveedor de IA con su coste estimado (§5.7). Va en una transacción propia
 * para que el registro de una llamada fallida se guarde aunque la operación que la pidió se deshaga.
 */
@Service
public class UsoIaServicio {

    private static final BigDecimal MILLON = new BigDecimal("1000000");
    private static final int DECIMALES_COSTE = 6;
    private static final String PROVEEDOR_SIMULADO = "simulado";

    private final UsoIaRepositorio usoIaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final IaProperties propiedades;

    public UsoIaServicio(UsoIaRepositorio usoIaRepositorio, UsuarioRepositorio usuarioRepositorio,
            IaProperties propiedades) {
        this.usoIaRepositorio = usoIaRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.propiedades = propiedades;
    }

    /**
     * @param usuarioId quien hizo la petición; nulo en llamadas del sistema
     * @param resultado respuesta del puerto; nula si la llamada falló sin respuesta (0 tokens)
     * @param latenciaMs duración de la llamada, reintento incluido
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(UUID usuarioId, FuncionIa funcion, ResultadoIa<?> resultado, long latenciaMs,
            boolean exito) {
        int tokensEntrada = resultado == null ? 0 : resultado.tokensEntrada();
        int tokensSalida = resultado == null ? 0 : resultado.tokensSalida();
        UsoIa uso = new UsoIa();
        uso.setUsuario(usuarioId == null ? null : usuarioRepositorio.getReferenceById(usuarioId));
        uso.setFuncion(funcion);
        uso.setProveedor(proveedorActual());
        uso.setModelo(resultado == null ? null : resultado.modelo());
        uso.setTokensEntrada(tokensEntrada);
        uso.setTokensSalida(tokensSalida);
        uso.setLatenciaMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, latenciaMs)));
        uso.setCosteEstimado(costeEstimado(tokensEntrada, tokensSalida));
        uso.setExito(exito);
        usoIaRepositorio.save(uso);
    }

    /** Proveedor que responde ahora: el configurado en modo real, "simulado" en modo simulado. */
    public String proveedorActual() {
        return propiedades.modo() == IaProperties.Modo.REAL ? propiedades.proveedor() : PROVEEDOR_SIMULADO;
    }

    /** USD = (entrada × precio entrada + salida × precio salida) / 1 000 000, con 6 decimales. */
    public BigDecimal costeEstimado(int tokensEntrada, int tokensSalida) {
        return propiedades.costeEntradaMillon().multiply(BigDecimal.valueOf(tokensEntrada))
                .add(propiedades.costeSalidaMillon().multiply(BigDecimal.valueOf(tokensSalida)))
                .divide(MILLON, DECIMALES_COSTE, RoundingMode.HALF_UP);
    }
}
