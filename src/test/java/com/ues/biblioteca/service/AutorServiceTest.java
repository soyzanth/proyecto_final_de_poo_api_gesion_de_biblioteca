package com.ues.biblioteca.service;

import com.ues.biblioteca.dto.AutorRequest;
import com.ues.biblioteca.dto.AutorResponse;
import com.ues.biblioteca.entity.Autor;
import com.ues.biblioteca.exception.ConflictException;
import com.ues.biblioteca.exception.ResourceNotFoundException;
import com.ues.biblioteca.repository.AutorRepository;
import com.ues.biblioteca.repository.LibroRepository;
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
class AutorServiceTest {

    @Mock
    private AutorRepository autorRepository;
    @Mock
    private LibroRepository libroRepository;

    @InjectMocks
    private AutorService autorService;

    private Autor crearAutor(Integer id, String nombre, String nacionalidad) {
        Autor autor = new Autor();
        autor.setId(id);
        autor.setNombre(nombre);
        autor.setNacionalidad(nacionalidad);
        return autor;
    }

    // ---------- listar ----------

    @Test
    void listar_devuelveTodosLosAutores() {
        when(autorRepository.findAll(any(Sort.class))).thenReturn(List.of(
                crearAutor(1, "George Orwell", "Britanica"),
                crearAutor(2, "Isaac Asimov", "Estadounidense")));

        List<AutorResponse> resultado = autorService.listar();

        assertEquals(2, resultado.size());
        assertEquals("George Orwell", resultado.get(0).nombre());
        assertEquals("Isaac Asimov", resultado.get(1).nombre());
    }

    // ---------- obtener ----------

    @Test
    void obtener_existente_devuelveElAutor() {
        when(autorRepository.findById(1))
                .thenReturn(Optional.of(crearAutor(1, "George Orwell", "Britanica")));

        AutorResponse resultado = autorService.obtener(1);

        assertEquals(1, resultado.id());
        assertEquals("George Orwell", resultado.nombre());
        assertEquals("Britanica", resultado.nacionalidad());
    }

    @Test
    void obtener_inexistente_lanzaResourceNotFound() {
        when(autorRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> autorService.obtener(99));
    }

    // ---------- crear ----------

    @Test
    void crear_guardaElAutorSinEspaciosSobrantes() {
        AutorRequest request = new AutorRequest("  Isaac Asimov  ", " Estadounidense ");
        when(autorRepository.save(any(Autor.class))).thenAnswer(inv -> {
            Autor a = inv.getArgument(0);
            a.setId(7);
            return a;
        });

        AutorResponse resultado = autorService.crear(request);

        ArgumentCaptor<Autor> captor = ArgumentCaptor.forClass(Autor.class);
        verify(autorRepository).save(captor.capture());
        assertEquals("Isaac Asimov", captor.getValue().getNombre());
        assertEquals("Estadounidense", captor.getValue().getNacionalidad());
        assertEquals(7, resultado.id());
        assertEquals("Isaac Asimov", resultado.nombre());
    }

    // ---------- actualizar ----------

    @Test
    void actualizar_existente_modificaLosDatos() {
        Autor existente = crearAutor(1, "G. Orwell", "Inglesa");
        when(autorRepository.findById(1)).thenReturn(Optional.of(existente));
        when(autorRepository.save(any(Autor.class))).thenAnswer(inv -> inv.getArgument(0));

        AutorResponse resultado = autorService.actualizar(1, new AutorRequest("George Orwell", "Britanica"));

        assertEquals(1, resultado.id());
        assertEquals("George Orwell", resultado.nombre());
        assertEquals("Britanica", resultado.nacionalidad());
    }

    @Test
    void actualizar_inexistente_lanzaResourceNotFoundYNoGuarda() {
        when(autorRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> autorService.actualizar(99, new AutorRequest("Nombre", "Pais")));
        verify(autorRepository, never()).save(any(Autor.class));
    }

    // ---------- eliminar ----------

    @Test
    void eliminar_conLibros_lanzaConflictYNoBorra() {
        Autor autor = crearAutor(1, "George Orwell", "Britanica");
        when(autorRepository.findById(1)).thenReturn(Optional.of(autor));
        when(libroRepository.existsByAutorId(1)).thenReturn(true);

        assertThrows(ConflictException.class, () -> autorService.eliminar(1));
        verify(autorRepository, never()).delete(any(Autor.class));
    }

    @Test
    void eliminar_sinLibros_borraElAutor() {
        Autor autor = crearAutor(1, "George Orwell", "Britanica");
        when(autorRepository.findById(1)).thenReturn(Optional.of(autor));
        when(libroRepository.existsByAutorId(1)).thenReturn(false);

        autorService.eliminar(1);

        verify(autorRepository).delete(autor);
    }

    @Test
    void eliminar_inexistente_lanzaResourceNotFound() {
        when(autorRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> autorService.eliminar(99));
        verify(autorRepository, never()).delete(any(Autor.class));
    }
}
