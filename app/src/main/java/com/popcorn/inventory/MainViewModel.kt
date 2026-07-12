@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.popcorn.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.popcorn.inventory.data.CategoriaSabor
import com.popcorn.inventory.data.ConfiguracionEntity
import com.popcorn.inventory.data.MovimientoInventarioEntity
import com.popcorn.inventory.data.PopcornRepository
import com.popcorn.inventory.data.PromocionConSabores
import com.popcorn.inventory.data.PromocionSaborInput
import com.popcorn.inventory.data.ReporteResumen
import com.popcorn.inventory.data.SaborEntity
import com.popcorn.inventory.data.SaborResumen
import com.popcorn.inventory.data.TipoMovimiento
import com.popcorn.inventory.data.VentaConDetalles
import com.popcorn.inventory.data.VentaEntity
import com.popcorn.inventory.data.VentaLineaInput
import com.popcorn.inventory.data.alFinalDelDiaMillis
import com.popcorn.inventory.data.alInicioDelDiaMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PopcornRepository =
        (application as PopcornApp).repository

    val fechaSeleccionada = MutableStateFlow(LocalDate.now())
    val pantallaReportes = MutableStateFlow(TipoReporte.DIA)
    val inicioRango = MutableStateFlow(LocalDate.now().minusDays(6))
    val finRango = MutableStateFlow(LocalDate.now())

    val configuracion: StateFlow<ConfiguracionEntity> =
        repository.configuracion.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfiguracionEntity())

    val sabores: StateFlow<List<SaborEntity>> =
        repository.sabores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val saboresActivos: StateFlow<List<SaborEntity>> =
        repository.saboresActivos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val saboresResumen: StateFlow<List<SaborResumen>> =
        repository.saboresResumen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val promociones: StateFlow<List<PromocionConSabores>> =
        repository.promociones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val movimientosRecientes: StateFlow<List<MovimientoInventarioEntity>> =
        repository.movimientosRecientes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ventasDia: StateFlow<List<VentaConDetalles>> =
        fechaSeleccionada.flatMapLatest { fecha ->
            repository.ventasEntre(fecha.alInicioDelDiaMillis(), fecha.alFinalDelDiaMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reporteDia: StateFlow<ReporteResumen> =
        fechaSeleccionada.flatMapLatest { fecha ->
            repository.reporteEntre(fecha.alInicioDelDiaMillis(), fecha.alFinalDelDiaMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReporteResumen(0, 0.0, emptyList(), 0))

    val reporteActual: StateFlow<ReporteResumen> =
        combine(pantallaReportes, fechaSeleccionada, inicioRango, finRango) { tipo, fecha, inicio, fin ->
            rangoParaReporte(tipo, fecha, inicio, fin)
        }.flatMapLatest { rango ->
            repository.reporteEntre(rango.first.alInicioDelDiaMillis(), rango.second.alFinalDelDiaMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReporteResumen(0, 0.0, emptyList(), 0))

    init {
        viewModelScope.launch {
            repository.asegurarConfiguracionInicial()
        }
    }

    fun cambiarFecha(dias: Long) {
        fechaSeleccionada.value = fechaSeleccionada.value.plusDays(dias)
    }

    fun seleccionarFecha(fecha: LocalDate) {
        fechaSeleccionada.value = fecha
    }

    fun hoy() {
        fechaSeleccionada.value = LocalDate.now()
    }

    fun crearSabor(nombre: String, categoria: String, ideal: Int, actual: Int, precio: Double?) {
        viewModelScope.launch {
            repository.crearSabor(nombre, categoria, ideal, actual, precio)
        }
    }

    fun actualizarSabor(sabor: SaborEntity) {
        viewModelScope.launch {
            repository.actualizarSabor(sabor)
        }
    }

    fun desactivarSabor(saborId: Long) {
        viewModelScope.launch {
            repository.desactivarSabor(saborId)
        }
    }

    fun reactivarSabor(saborId: Long) {
        viewModelScope.launch {
            repository.reactivarSabor(saborId)
        }
    }

    fun borrarODesactivarSabor(saborId: Long) {
        viewModelScope.launch {
            repository.borrarODesactivarSabor(saborId)
        }
    }

    fun registrarVentaNormal(sabor: SaborEntity, cantidad: Int) {
        viewModelScope.launch {
            repository.registrarVentaNormal(sabor, cantidad, fechaSeleccionada.value.alInicioDelDiaMillis())
        }
    }

    fun registrarVentaLineas(lineas: List<VentaLineaInput>, fecha: LocalDate = fechaSeleccionada.value) {
        viewModelScope.launch {
            repository.registrarVentaLineas(lineas, fecha.alInicioDelDiaMillis())
        }
    }

    fun registrarVentaPromocion(promocion: PromocionConSabores, fecha: LocalDate) {
        viewModelScope.launch {
            repository.registrarVentaPromocion(promocion, fecha.alInicioDelDiaMillis())
        }
    }

    fun actualizarVenta(
        venta: VentaEntity,
        lineas: List<VentaLineaInput>,
        fecha: LocalDate,
        promocion: PromocionConSabores?
    ) {
        viewModelScope.launch {
            repository.actualizarVenta(venta, lineas, fecha.alInicioDelDiaMillis(), promocion)
        }
    }

    fun eliminarVenta(ventaId: Long) {
        viewModelScope.launch {
            repository.eliminarVenta(ventaId)
        }
    }

    fun registrarPedidoRecibido(saborId: Long, cantidad: Int) {
        registrarMovimiento(saborId, TipoMovimiento.PEDIDO_RECIBIDO, cantidad, "Pedido recibido")
    }

    fun registrarAjuste(saborId: Long, tipo: String, cantidad: Int, motivo: String) {
        registrarMovimiento(saborId, tipo, cantidad, motivo)
    }

    fun actualizarMovimiento(
        movimientoId: Long,
        saborId: Long,
        tipo: String,
        cantidad: Int,
        fecha: LocalDate,
        motivo: String
    ) {
        viewModelScope.launch {
            repository.actualizarMovimientoInventario(
                movimientoId = movimientoId,
                nuevoSaborId = saborId,
                nuevoTipo = tipo,
                nuevaCantidad = cantidad,
                nuevaFechaMovimiento = fecha.alInicioDelDiaMillis(),
                nuevoMotivo = motivo
            )
        }
    }

    fun guardarConfiguracion(configuracion: ConfiguracionEntity) {
        viewModelScope.launch {
            repository.guardarConfiguracion(configuracion)
        }
    }

    fun aplicarPrecioASeleccion(saborIds: List<Long>, precio: Double) {
        viewModelScope.launch {
            repository.aplicarPrecioASeleccion(saborIds, precio)
        }
    }

    fun aplicarPrecioACategorias(categorias: List<String>, precio: Double) {
        viewModelScope.launch {
            repository.aplicarPrecioACategorias(categorias, precio)
        }
    }

    fun crearPromocion(
        nombre: String,
        precio: Double,
        inicio: LocalDate,
        fin: LocalDate?,
        sabores: List<PromocionSaborInput>
    ) {
        viewModelScope.launch {
            repository.crearPromocion(
                nombre = nombre,
                precioPromocional = precio,
                fechaInicio = inicio.alInicioDelDiaMillis(),
                fechaFin = fin?.alFinalDelDiaMillis(),
                sabores = sabores
            )
        }
    }

    fun desactivarPromocion(promocionId: Long) {
        viewModelScope.launch {
            repository.desactivarPromocion(promocionId)
        }
    }

    fun reactivarPromocion(promocionId: Long) {
        viewModelScope.launch {
            repository.reactivarPromocion(promocionId)
        }
    }

    private fun registrarMovimiento(saborId: Long, tipo: String, cantidad: Int, motivo: String?) {
        viewModelScope.launch {
            repository.registrarMovimientoInventario(
                saborId = saborId,
                tipo = tipo,
                cantidad = cantidad,
                fechaMovimiento = fechaSeleccionada.value.alInicioDelDiaMillis(),
                motivo = motivo
            )
        }
    }
}

enum class TipoReporte {
    DIA,
    SEMANA,
    MES,
    RANGO
}

fun rangoParaReporte(
    tipo: TipoReporte,
    fecha: LocalDate,
    inicio: LocalDate,
    fin: LocalDate
): Pair<LocalDate, LocalDate> {
    return when (tipo) {
        TipoReporte.DIA -> fecha to fecha
        TipoReporte.SEMANA -> {
            val lunes = fecha.with(DayOfWeek.MONDAY)
            lunes to lunes.plusDays(6)
        }
        TipoReporte.MES -> {
            val primero = fecha.withDayOfMonth(1)
            primero to primero.withDayOfMonth(primero.lengthOfMonth())
        }
        TipoReporte.RANGO -> {
            if (inicio <= fin) inicio to fin else fin to inicio
        }
    }
}
