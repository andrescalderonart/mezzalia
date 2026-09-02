package com.mezzalia.pedido.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validacion de Bean Validation pura (jakarta.validation), sin contexto de
 * Spring -- @Size solo se ejerce via @Valid en el limite del controlador
 * (MenuPublicoController.crearPedido), asi que PedidoServiceTest (que llama
 * a PedidoService directo) nunca pasa por aca. Ver el comentario en
 * CrearPedidoRequest sobre por que 30 no es un numero arbitrario de UI: es
 * el limite real contra el abuso del endpoint publico sin auth.
 */
class CrearPedidoRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void crearValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidator() {
        factory.close();
    }

    @Test
    void unaListaDeMasDeTreintaItemsDistintosEsInvalida() {
        CrearPedidoRequest request = new CrearPedidoRequest(
                UUID.randomUUID().toString(), itemsDistintos(31), null, null);

        Set<ConstraintViolation<CrearPedidoRequest>> violaciones = validator.validate(request);

        assertThat(violaciones)
                .as("31 items distintos deberia violar @Size(max=30) en items")
                .anyMatch(v -> v.getPropertyPath().toString().equals("items"));
    }

    @Test
    void unaListaDeExactamenteTreintaItemsEsValida() {
        CrearPedidoRequest request = new CrearPedidoRequest(
                UUID.randomUUID().toString(), itemsDistintos(30), null, null);

        Set<ConstraintViolation<CrearPedidoRequest>> violaciones = validator.validate(request);

        assertThat(violaciones).isEmpty();
    }

    @Test
    void unaListaVaciaEsInvalidaPorNotEmptyNoPorSize() {
        CrearPedidoRequest request = new CrearPedidoRequest(
                UUID.randomUUID().toString(), List.of(), null, null);

        Set<ConstraintViolation<CrearPedidoRequest>> violaciones = validator.validate(request);

        assertThat(violaciones).anyMatch(v -> v.getPropertyPath().toString().equals("items"));
    }

    private List<ItemSolicitadoRequest> itemsDistintos(int cantidad) {
        List<ItemSolicitadoRequest> items = new ArrayList<>();
        for (long id = 1; id <= cantidad; id++) {
            items.add(new ItemSolicitadoRequest(id, 1));
        }
        return items;
    }
}
