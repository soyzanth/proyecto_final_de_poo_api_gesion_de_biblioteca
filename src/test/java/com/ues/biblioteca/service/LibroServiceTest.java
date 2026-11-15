package com.ues.biblioteca.service;

import com.ues.biblioteca.dto.LibroRequest;
import com.ues.biblioteca.dto.LibroResponse;
import com.ues.biblioteca.entity.Autor;
import com.ues.biblioteca.entity.Libro;
import com.ues.biblioteca.exception.ConflictException;
import com.ues.biblioteca.exception.ResourceNotFoundException;
import com.ues.biblioteca.repository.AutorRepository;
import com.ues.biblioteca.repository.LibroRepository;
import com.ues.biblioteca.repository.PrestamoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibroServiceTest {

    @Mock
    private LibroRepository libroRepository;
    @Mock
    private AutorRepository autorRepository;
    @Mock
    private PrestamoRepository prestamoRepository;

    @InjectMocks
    private LibroService libroService;

    private Autor autor;

    @BeforeEach
    void setUp() {
        autor = new Autor();
        autor.setId(1);
        autor.setNombre("Gabriel Garcia Marquez");
    }

    private Libro crearLibro(Integer id, String titulo, boolean disponible) {
        Libro libro = new Libro();
        libro.setId(id);
        libro.setTitulo(titulo);
        libro.setIsbn("978-" + id);
        libro.setAnioPublicacion(2000);
        libro.setEditorial("Editorial Test");
        libro.setDisponible(disponible);
        libro.setAutor(autor);
        return libro;
    }

    // ---------- listar ----------

    @Test
    void listar_sinFiltros_devuelveTodosLosLibros() {
        when(libroRepository.findAll(any(Sort.class))).thenReturn(List.of(
                crearLibro(1, "Cien anios de soledad", true),
                crearLibro(2, "El coronel no tiene quien le escriba", false)));

        List<LibroResponse> resultado = libroService.listar(null, null, null);

        assertEquals(2, resultado.size());
    }

    @Test
    void listar_filtraPorDisponible() {
        when(libroRepository.findAll(any(Sort.class))).thenReturn(List.of(
                crearLibro(1, "Cien anios de soledad", true),
                crearLibro(2, "El coronel no tiene quien le escriba", false)));

        List<LibroResponse> resultado = libroService.listar(true, null, null);

        assertEquals(1, resultado.size());
        assertEquals("Cien anios de soledad", resultado.get(0).titulo());
    }

    @Test
    void listar_filtraPorTituloSinImportarMayusculas() {
        when(libroRepository.findAll(any(Sort.class))).thenReturn(List.of(
                crearLibro(1, "Cien anios de soledad", true),
                crearLibro(2, "El coronel no tiene quien le escriba", false)));

        List<LibroResponse> resultado = libroService.listar(null, null, "  CORONEL ");

        assertEquals(1, resultado.size());
        assertEquals(2, resultado.get(0).id());
    }

    // ---------- obtener ----------

    @Test
    void obtener_existente_devuelveElLibro() {
        when(libroRepository.findById(1))
                .thenReturn(Optional.of(crearLibro(1, "Cien anios de soledad", true)));

        LibroResponse resultado = libroService.obtener(1);

        assertEquals("Cien anios de soledad", resultado.titulo());
    }

    @Test
    void obtener_inexistente_lanzaResourceNotFound() {
        when(libroRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> libroService.obtener(99));
    }

    // ---------- crear ----------

    @Test
    void crear_exitoso_guardaLibroDisponibleYConDatosLimpios() {
        LibroRequest request = mock(LibroRequest.class);
        when(request.isbn()).thenReturn(" 978-1 ");
        when(request.titulo()).thenReturn(" Cien anios de soledad ");
        when(request.editorial()).thenReturn(" Sudamericana ");
        when(request.autorId()).thenReturn(1);
        when(libroRepository.existsByIsbn("978-1")).thenReturn(false);
        when(autorRepository.findById(1)).thenReturn(Optional.of(autor));
        when(libroRepository.save(any(Libro.class))).thenAnswer(inv -> {
            Libro l = inv.getArgument(0);
            l.setId(10);
            return l;
        });

        LibroResponse resultado = libroService.crear(request);

        assertEquals(10, resultado.id());
        assertEquals("Cien anios de soledad", resultado.titulo());
        assertEquals("978-1", resultado.isbn());
        assertTrue(resultado.disponible());
    }

    @Test
    void crear_isbnDuplicado_lanzaConflict() {
        LibroRequest request = mock(LibroRequest.class);
        when(request.isbn()).thenReturn("978-1");
        when(libroRepository.existsByIsbn("978-1")).thenReturn(true);

        assertThrows(ConflictException.class, () -> libroService.crear(request));
        verify(libroRepository, never()).save(any(Libro.class));
    }

    @Test
    void crear_autorInexistente_lanzaResourceNotFound() {
        LibroRequest request = mock(LibroRequest.class);
        when(request.isbn()).thenReturn("978-1");
        when(request.autorId()).thenReturn(5);
        when(libroRepository.existsByIsbn("978-1")).thenReturn(false);
        when(autorRepository.findById(5)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> libroService.crear(request));
        verify(libroRepository, never()).save(any(Libro.class));
    }

    // ---------- eliminar ----------

    @Test
    void eliminar_conPrestamos_lanzaConflictYNoBorra() {
        Libro libro = crearLibro(1, "Cien anios de soledad", false);
        when(libroRepository.findById(1)).thenReturn(Optional.of(libro));
        when(prestamoRepository.existsByLibroId(1)).thenReturn(true);

        assertThrows(ConflictException.class, () -> libroService.eliminar(1));
        verify(libroRepository, never()).delete(any(Libro.class));
    }

    @Test
    void eliminar_sinPrestamos_borraElLibro() {
        Libro libro = crearLibro(1, "Cien anios de soledad", true);
        when(libroRepository.findById(1)).thenReturn(Optional.of(libro));
        when(prestamoRepository.existsByLibroId(1)).thenReturn(false);

        libroService.eliminar(1);

        verify(libroRepository).delete(libro);
    }
}