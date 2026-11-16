package com.ues.biblioteca.service;

import com.ues.biblioteca.dto.PrestamoRequest;
import com.ues.biblioteca.dto.PrestamoResponse;
import com.ues.biblioteca.entity.Libro;
import com.ues.biblioteca.entity.Prestamo;
import com.ues.biblioteca.entity.Socio;
import com.ues.biblioteca.exception.BusinessException;
import com.ues.biblioteca.exception.ConflictException;
import com.ues.biblioteca.exception.ResourceNotFoundException;
import com.ues.biblioteca.repository.LibroRepository;
import com.ues.biblioteca.repository.PrestamoRepository;
import com.ues.biblioteca.repository.SocioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrestamoServiceTest {

    @Mock
    private PrestamoRepository prestamoRepository;
    @Mock
    private LibroRepository libroRepository;
    @Mock
    private SocioRepository socioRepository;

    @InjectMocks
    private PrestamoService prestamoService;

    private Libro libro;
    private Socio socio;

    @BeforeEach
    void setUp() {
        libro = new Libro();
        libro.setId(1);
        libro.setTitulo("1984");
        libro.setDisponible(true);

        socio = new Socio();
        socio.setId(1);
        socio.setNombre("Ana Perez");
    }

    private Prestamo crearPrestamo(Integer id, Libro libro, Socio socio, LocalDate fechaPrestamo, boolean devuelto) {
        Prestamo prestamo = new Prestamo();
        prestamo.setId(id);
        prestamo.setLibro(libro);
        prestamo.setSocio(socio);
        prestamo.setFechaPrestamo(fechaPrestamo);
        prestamo.setDevuelto(devuelto);
        return prestamo;
    }

    // ---------- listar ----------

    @Test
    void listar_sinFiltros_devuelveTodosLosPrestamos() {
        LocalDate hoy = LocalDate.now();
        when(prestamoRepository.findAll(any(Sort.class))).thenReturn(List.of(
                crearPrestamo(1, libro, socio, hoy, false),
                crearPrestamo(2, libro, socio, hoy, true)));

        List<PrestamoResponse> resultado = prestamoService.listar(null, null, null);

        assertEquals(2, resultado.size());
    }

    @Test
    void listar_estadoActivo_devuelveSoloLosNoDevueltos() {
        LocalDate hoy = LocalDate.now();
        when(prestamoRepository.findAll(any(Sort.class))).thenReturn(List.of(
                crearPrestamo(1, libro, socio, hoy, false),
                crearPrestamo(2, libro, socio, hoy, true)));

        List<PrestamoResponse> resultado = prestamoService.listar("activo", null, null);

        assertEquals(1, resultado.size());
        assertEquals(1, resultado.get(0).id());
        assertFalse(resultado.get(0).devuelto());
    }

    @Test
    void listar_estadoDevuelto_devuelveSoloLosDevueltos() {
        LocalDate hoy = LocalDate.now();
        when(prestamoRepository.findAll(any(Sort.class))).thenReturn(List.of(
                crearPrestamo(1, libro, socio, hoy, false),
                crearPrestamo(2, libro, socio, hoy, true)));

        List<PrestamoResponse> resultado = prestamoService.listar("DEVUELTO", null, null);

        assertEquals(1, resultado.size());
        assertTrue(resultado.get(0).devuelto());
    }

    @Test
    void listar_estadoInvalido_lanzaBusinessException() {
        assertThrows(BusinessException.class, () -> prestamoService.listar("cualquiera", null, null));
    }

    @Test
    void listar_filtraPorSocio() {
        Socio otro = new Socio();
        otro.setId(2);
        otro.setNombre("Luis Aguilar");
        LocalDate hoy = LocalDate.now();
        when(prestamoRepository.findAll(any(Sort.class))).thenReturn(List.of(
                crearPrestamo(1, libro, socio, hoy, false),
                crearPrestamo(2, libro, otro, hoy, false)));

        List<PrestamoResponse> resultado = prestamoService.listar(null, 2, null);

        assertEquals(1, resultado.size());
        assertEquals("Luis Aguilar", resultado.get(0).socioNombre());
    }

    // ---------- obtener ----------

    @Test
    void obtener_existente_devuelveElPrestamo() {
        when(prestamoRepository.findById(1))
                .thenReturn(Optional.of(crearPrestamo(1, libro, socio, LocalDate.now(), false)));

        PrestamoResponse resultado = prestamoService.obtener(1);

        assertEquals(1, resultado.id());
        assertEquals("1984", resultado.libroTitulo());
        assertEquals("Ana Perez", resultado.socioNombre());
    }

    @Test
    void obtener_inexistente_lanzaResourceNotFound() {
        when(prestamoRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> prestamoService.obtener(99));
    }

    // ---------- crear ----------

    @Test
    void crear_exitoso_marcaElLibroComoNoDisponibleYUsaLaFechaDeHoy() {
        when(libroRepository.findById(1)).thenReturn(Optional.of(libro));
        when(socioRepository.findById(1)).thenReturn(Optional.of(socio));
        when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(inv -> {
            Prestamo p = inv.getArgument(0);
            p.setId(5);
            return p;
        });

        PrestamoResponse resultado = prestamoService.crear(new PrestamoRequest(1, 1, null));

        assertEquals(5, resultado.id());
        assertEquals(LocalDate.now(), resultado.fechaPrestamo());
        assertFalse(resultado.devuelto());
        assertNull(resultado.fechaDevolucion());
        assertFalse(libro.getDisponible());
    }

    @Test
    void crear_conFechaIndicada_usaEsaFecha() {
        LocalDate fecha = LocalDate.now().minusDays(3);
        when(libroRepository.findById(1)).thenReturn(Optional.of(libro));
        when(socioRepository.findById(1)).thenReturn(Optional.of(socio));
        when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(inv -> inv.getArgument(0));

        PrestamoResponse resultado = prestamoService.crear(new PrestamoRequest(1, 1, fecha));

        assertEquals(fecha, resultado.fechaPrestamo());
    }

    @Test
    void crear_libroInexistente_lanzaResourceNotFound() {
        when(libroRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> prestamoService.crear(new PrestamoRequest(99, 1, null)));
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    void crear_socioInexistente_lanzaResourceNotFound() {
        when(libroRepository.findById(1)).thenReturn(Optional.of(libro));
        when(socioRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> prestamoService.crear(new PrestamoRequest(1, 99, null)));
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    void crear_libroNoDisponible_lanzaConflictYNoGuarda() {
        libro.setDisponible(false);
        when(libroRepository.findById(1)).thenReturn(Optional.of(libro));
        when(socioRepository.findById(1)).thenReturn(Optional.of(socio));

        assertThrows(ConflictException.class,
                () -> prestamoService.crear(new PrestamoRequest(1, 1, null)));
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    void crear_libroConPrestamoActivo_lanzaConflictYNoGuarda() {
        when(libroRepository.findById(1)).thenReturn(Optional.of(libro));
        when(socioRepository.findById(1)).thenReturn(Optional.of(socio));
        when(prestamoRepository.existsByLibroIdAndDevueltoFalse(1)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> prestamoService.crear(new PrestamoRequest(1, 1, null)));
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    // ---------- devolver ----------

    @Test
    void devolver_sinFecha_registraLaDevolucionYLiberaElLibro() {
        libro.setDisponible(false);
        Prestamo prestamo = crearPrestamo(1, libro, socio, LocalDate.now().minusDays(5), false);
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));

        PrestamoResponse resultado = prestamoService.devolver(1, null);

        assertTrue(resultado.devuelto());
        assertEquals(LocalDate.now(), resultado.fechaDevolucion());
        assertTrue(libro.getDisponible());
    }

    @Test
    void devolver_conFechaValida_usaEsaFecha() {
        LocalDate fechaPrestamo = LocalDate.now().minusDays(10);
        LocalDate fechaDevolucion = LocalDate.now().minusDays(2);
        libro.setDisponible(false);
        Prestamo prestamo = crearPrestamo(1, libro, socio, fechaPrestamo, false);
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));

        PrestamoResponse resultado = prestamoService.devolver(1, fechaDevolucion);

        assertEquals(fechaDevolucion, resultado.fechaDevolucion());
    }

    @Test
    void devolver_prestamoYaDevuelto_lanzaConflict() {
        Prestamo prestamo = crearPrestamo(1, libro, socio, LocalDate.now().minusDays(5), true);
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));

        assertThrows(ConflictException.class, () -> prestamoService.devolver(1, null));
    }

    @Test
    void devolver_fechaAnteriorAlPrestamo_lanzaBusinessExceptionYNoLiberaElLibro() {
        libro.setDisponible(false);
        Prestamo prestamo = crearPrestamo(1, libro, socio, LocalDate.now().minusDays(5), false);
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));

        assertThrows(BusinessException.class,
                () -> prestamoService.devolver(1, LocalDate.now().minusDays(6)));
        assertFalse(libro.getDisponible());
        assertFalse(prestamo.getDevuelto());
    }

    @Test
    void devolver_fechaFutura_lanzaBusinessException() {
        Prestamo prestamo = crearPrestamo(1, libro, socio, LocalDate.now().minusDays(5), false);
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));

        assertThrows(BusinessException.class,
                () -> prestamoService.devolver(1, LocalDate.now().plusDays(1)));
    }

    @Test
    void devolver_inexistente_lanzaResourceNotFound() {
        when(prestamoRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> prestamoService.devolver(99, null));
    }

    // ---------- eliminar ----------

    @Test
    void eliminar_prestamoActivo_lanzaConflictYNoBorra() {
        Prestamo prestamo = crearPrestamo(1, libro, socio, LocalDate.now(), false);
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));

        assertThrows(ConflictException.class, () -> prestamoService.eliminar(1));
        verify(prestamoRepository, never()).delete(any(Prestamo.class));
    }

    @Test
    void eliminar_prestamoDevuelto_loBorra() {
        Prestamo prestamo = crearPrestamo(1, libro, socio, LocalDate.now().minusDays(3), true);
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));

        prestamoService.eliminar(1);

        verify(prestamoRepository).delete(prestamo);
    }

    @Test
    void eliminar_inexistente_lanzaResourceNotFound() {
        when(prestamoRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> prestamoService.eliminar(99));
    }
}
