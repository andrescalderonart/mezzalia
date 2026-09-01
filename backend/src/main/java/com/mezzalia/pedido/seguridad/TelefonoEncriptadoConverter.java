package com.mezzalia.pedido.seguridad;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * AttributeConverter de JPA para Comensal.telefono (documentacion-tecnica.md
 * §10). `autoApply = false` (el default) a proposito y explicito aca en el
 * comentario: este converter se aplica SOLO donde el campo lo declara
 * explicitamente con @Convert (ver Comensal.java) -- nunca global/implicito
 * para cualquier String de cualquier entidad, que seria facil de romper por
 * accidente el dia que alguien agregue un String nuevo a otra entidad.
 *
 * NO es un bean de Spring (sin @Component): Hibernate instancia los
 * AttributeConverter por reflexion, con un constructor sin argumentos, como
 * parte del bootstrap de JPA -- no pasa por el contenedor de Spring, asi que
 * no podria recibir nada por @Autowired/@Value aunque quisiera. Por eso la
 * clave de cifrado se resuelve en AesGcmComensalCipher via System.getenv()
 * directamente, no via inyeccion.
 */
@Converter
public class TelefonoEncriptadoConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String telefonoEnTextoPlano) {
        if (telefonoEnTextoPlano == null) {
            return null;
        }
        return AesGcmComensalCipher.cifrar(telefonoEnTextoPlano);
    }

    @Override
    public String convertToEntityAttribute(String valorCifradoEnColumna) {
        if (valorCifradoEnColumna == null) {
            return null;
        }
        return AesGcmComensalCipher.descifrar(valorCifradoEnColumna);
    }
}
