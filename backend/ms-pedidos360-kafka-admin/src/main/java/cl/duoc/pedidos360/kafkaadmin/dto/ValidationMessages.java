package cl.duoc.pedidos360.kafkaadmin.dto;

/**
 * Los mensajes de las validaciones propias incluyen texto que envia el cliente (por ejemplo, la
 * clave de una configuracion). Hibernate Validator interpreta {@code {...}} y {@code ${...}} en las
 * plantillas de mensaje, asi que ese texto se escapa para que se muestre tal cual y no se evalue.
 */
final class ValidationMessages {

    private ValidationMessages() {
    }

    static String escape(String message) {
        return message
                .replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("$", "\\$");
    }
}
