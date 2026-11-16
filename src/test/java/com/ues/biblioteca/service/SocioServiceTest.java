package com.ues.biblioteca.service;

import com.ues.biblioteca.dto.SocioRequest;
import com.ues.biblioteca.dto.SocioResponse;
import com.ues.biblioteca.entity.Socio;
import com.ues.biblioteca.exception.ConflictException;
import com.ues.biblioteca.exception.ResourceNotFoundException;
import com.ues.biblioteca.repository.PrestamoRepository;
import com.ues.biblioteca.repository.SocioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocioServiceTest {

    @Mock
    private SocioRepository socioRepository;
    @Mock
    private PrestamoRepository prestamoRepository;

    @InjectMocks
    private SocioService socioService;

    private Socio crearSocio(Integer id, String nombre, String numeroSocio, String email) {
        Socio socio = new Socio();
        socio.setId(id);
        socio.setNombre(nombre);
        socio.setNumeroSocio(numeroSocio);
        socio.setEmail(email);
        return socio;
    }

    // ---------- listar ----------

    @Test
    void listar_devuelveTodosLosSocios() {
        when(socioRepository.findAll(any(Sort.class))).thenReturn(List.of(
                crearSocio(1, "Ana Perez", "AP001", "ap001@ues.edu.sv"),
                crearSocio(2, "Luis Aguilar", "LA002", "la002@ues.edu.sv")));

        List<SocioResponse> resultado = socioService.listar();

        assertEquals(2, resultado.size());
        assertEquals("AP001", resultado.get(0).numeroSocio());
    }

    // ---------- obtener ----------

    @Test
    void obtener_existente_devuelveElSocio() {
        when(socioRepository.findById(1))
                .thenReturn(Optional.of(crearSocio(1, "Ana Perez", "AP001", "ap001@ues.edu.sv")));

        SocioResponse resultado = socioService.obtener(1);

        assertEquals("Ana Perez", resultado.nombre());
        assertEquals("ap001@ues.edu.sv", resultado.email());
    }

    @Test
    void obtener_inexistente_lanzaResourceNotFound() {
        when(socioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> socioService.obtener(99));
    }

    // ---------- crear ----------

    @Test
    void crear_exitoso_limpiaEspaciosYPasaElEmailAMinusculas() {
        SocioRequest request = new SocioRequest(" Ana Perez ", " AP001 ", " AP001@UES.EDU.SV ");
        when(socioRepository.save(any(Socio.class))).thenAnswer(inv -> {
            Socio s = inv.getArgument(0);
            s.setId(5);
            return s;
        });

        SocioResponse resultado = socioService.crear(request);

        ArgumentCaptor<Socio> captor = ArgumentCaptor.forClass(Socio.class);
        verify(socioRepository).save(captor.capture());
        assertEquals("Ana Perez", captor.getValue().getNombre());
        assertEquals("AP001", captor.getValue().getNumeroSocio());
        assertEquals("ap001@ues.edu.sv", captor.getValue().getEmail());
        assertEquals(5, resultado.id());
    }

    @Test
    void crear_numeroDeSocioDuplicado_lanzaConflictYNoGuarda() {
        SocioRequest request = new SocioRequest("Ana Perez", "AP001", "ap001@ues.edu.sv");
        when(socioRepository.existsByNumeroSocio("AP001")).thenReturn(true);

        assertThrows(ConflictException.class, () -> socioService.crear(request));
        verify(socioRepository, never()).save(any(Socio.class));
    }

    @Test
    void crear_emailDuplicado_lanzaConflictYNoGuarda() {
        SocioRequest request = new SocioRequest("Ana Perez", "AP001", "ap001@ues.edu.sv");
        when(socioRepository.existsByEmail("ap001@ues.edu.sv")).thenReturn(true);

        assertThrows(ConflictException.class, () -> socioService.crear(request));
        verify(socioRepository, never()).save(any(Socio.class));
    }

    // ---------- actualizar ----------

    @Test
    void actualizar_existente_modificaLosDatos() {
        Socio existente = crearSocio(1, "Ana P.", "AP001", "viejo@ues.edu.sv");
        when(socioRepository.findById(1)).thenReturn(Optional.of(existente));
        when(socioRepository.save(any(Socio.class))).thenAnswer(inv -> inv.getArgument(0));

        SocioResponse resultado = socioService.actualizar(1,
                new SocioRequest("Ana Perez", "AP001", "nuevo@ues.edu.sv"));

        assertEquals("Ana Perez", resultado.nombre());
        assertEquals("nuevo@ues.edu.sv", resultado.email());
    }

    @Test
    void actualizar_inexistente_lanzaResourceNotFound() {
        when(socioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> socioService.actualizar(99,
                new SocioRequest("Ana Perez", "AP001", "ap001@ues.edu.sv")));
        verify(socioRepository, never()).save(any(Socio.class));
    }

    @Test
    void actualizar_numeroDeSocioDeOtroSocio_lanzaConflict() {
        when(socioRepository.findById(1))
                .thenReturn(Optional.of(crearSocio(1, "Ana Perez", "AP001", "ap001@ues.edu.sv")));
        when(socioRepository.existsByNumeroSocioAndIdNot("LA002", 1)).thenReturn(true);

        assertThrows(ConflictException.class, () -> socioService.actualizar(1,
                new SocioRequest("Ana Perez", "LA002", "ap001@ues.edu.sv")));
        verify(socioRepository, never()).save(any(Socio.class));
    }

    // ---------- eliminar ----------

    @Test
    void eliminar_conPrestamos_lanzaConflictYNoBorra() {
        Socio socio = crearSocio(1, "Ana Perez", "AP001", "ap001@ues.edu.sv");
        when(socioRepository.findById(1)).thenReturn(Optional.of(socio));
        when(prestamoRepository.existsBySocioId(1)).thenReturn(true);

        assertThrows(ConflictException.class, () -> socioService.eliminar(1));
        verify(socioRepository, never()).delete(any(Socio.class));
    }

    @Test
    void eliminar_sinPrestamos_borraElSocio() {
        Socio socio = crearSocio(1, "Ana Perez", "AP001", "ap001@ues.edu.sv");
        when(socioRepository.findById(1)).thenReturn(Optional.of(socio));
        when(prestamoRepository.existsBySocioId(1)).thenReturn(false);

        socioService.eliminar(1);

        verify(socioRepository).delete(socio);
    }

    @Test
    void eliminar_inexistente_lanzaResourceNotFound() {
        when(socioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> socioService.eliminar(99));
    }
}
