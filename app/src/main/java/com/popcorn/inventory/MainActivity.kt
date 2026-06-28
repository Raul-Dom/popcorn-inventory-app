@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.popcorn.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.popcorn.inventory.data.CategoriaSabor
import com.popcorn.inventory.data.ConfiguracionEntity
import com.popcorn.inventory.data.MovimientoInventarioEntity
import com.popcorn.inventory.data.PromocionConSabores
import com.popcorn.inventory.data.PromocionEntity
import com.popcorn.inventory.data.SaborEntity
import com.popcorn.inventory.data.SaborResumen
import com.popcorn.inventory.data.TipoMovimiento
import com.popcorn.inventory.data.VentaConDetalles
import com.popcorn.inventory.data.VentaLineaInput
import com.popcorn.inventory.ui.PopcornTheme
import com.popcorn.inventory.ui.formatoDinero
import com.popcorn.inventory.ui.formatoFechaNegocio
import com.popcorn.inventory.ui.formatoUnidades
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PopcornTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PopcornInventoryApp()
                }
            }
        }
    }
}

private enum class AppTab(val titulo: String, val icono: String) {
    VENTAS("Ventas", "$"),
    INVENTARIO("Inventario", "#"),
    CONFIGURACION("Config.", "*"),
    REPORTES("Reportes", "%")
}

private data class VentaLineaUi(
    val id: Int,
    val saborId: Long?,
    val cantidad: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PopcornInventoryApp(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf(AppTab.VENTAS) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("La Pop-Pería", fontWeight = FontWeight.SemiBold)
                        Text("Ventas físicas y control por bolsas", style = MaterialTheme.typography.bodySmall)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = { Text(item.icono, fontWeight = FontWeight.Bold) },
                        label = { Text(item.titulo) }
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (tab) {
            AppTab.VENTAS -> VentasScreen(vm, modifier)
            AppTab.INVENTARIO -> InventarioScreen(vm, modifier)
            AppTab.CONFIGURACION -> ConfiguracionScreen(vm, modifier)
            AppTab.REPORTES -> ReportesScreen(vm, modifier)
        }
    }
}

@Composable
private fun VentasScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val fecha by vm.fechaSeleccionada.collectAsState()
    val sabores by vm.saboresActivos.collectAsState()
    val resumen by vm.saboresResumen.collectAsState()
    val ventas by vm.ventasDia.collectAsState()
    val reporte by vm.reporteDia.collectAsState()
    val promociones by vm.promociones.collectAsState()
    var busqueda by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("TODAS") }
    var ventaNormalSabor by remember { mutableStateOf<SaborEntity?>(null) }
    var ventaPromoSabor by remember { mutableStateOf<SaborEntity?>(null) }
    var mostrarAgregarVenta by remember { mutableStateOf(false) }
    var ventaEditando by remember { mutableStateOf<VentaConDetalles?>(null) }

    val resumenPorId = remember(resumen) { resumen.associateBy { it.id } }
    val saboresFiltrados = remember(sabores, categoria, busqueda) {
        sabores.filter {
            (categoria == "TODAS" || it.categoria == categoria) &&
                it.nombre.contains(busqueda, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FechaHeader(
                fecha = fecha,
                onAnterior = { vm.cambiarFecha(-1) },
                onSiguiente = { vm.cambiarFecha(1) },
                onHoy = { vm.hoy() },
                onSeleccionarFecha = { vm.seleccionarFecha(it) }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Ventas", style = MaterialTheme.typography.headlineSmall)
                    Text("Captura una o varias líneas", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { mostrarAgregarVenta = true }) { Text("Agregar venta") }
            }
        }
        item {
            ResumenVentasCard(
                totalUnidades = reporte.totalUnidades,
                totalDinero = reporte.totalDinero,
                pedidoSugeridoTotal = reporte.pedidoSugeridoTotal
            )
        }
        item {
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                label = { Text("Buscar sabor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            CategoriaFiltro(categoria, onCategoria = { categoria = it })
        }
        items(saboresFiltrados, key = { sabor -> "sabor-${sabor.id}" }) { sabor ->
            val item = resumenPorId[sabor.id]
            SaborVentaCard(
                sabor = sabor,
                resumen = item,
                promociones = promocionesActivasPara(promociones, sabor, fecha),
                onVenta = { ventaNormalSabor = sabor },
                onPromo = { ventaPromoSabor = sabor }
            )
        }
        item {
            Text("Ventas de la fecha", style = MaterialTheme.typography.titleMedium)
        }
        if (ventas.isEmpty()) {
            item { EmptyState("No hay ventas registradas para esta fecha.") }
        } else {
            items(ventas, key = { venta -> "venta-${venta.venta.id}" }) { venta ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${venta.venta.totalUnidades.formatoUnidades()} bolsas - ${venta.venta.totalDinero.formatoDinero()}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (venta.venta.promocionId == null) "Venta normal" else "Venta con promoción",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            venta.detalles.joinToString { detalle ->
                                val nombre = sabores.firstOrNull { it.id == detalle.saborId }?.nombre ?: "Sabor inactivo"
                                "$nombre x${detalle.cantidad.formatoUnidades()}"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = { ventaEditando = venta }) { Text("Editar") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    ventaNormalSabor?.let { sabor ->
        VentaNormalDialog(
            sabor = sabor,
            onDismiss = { ventaNormalSabor = null },
            onConfirm = { cantidad ->
                vm.registrarVentaNormal(sabor, cantidad)
                ventaNormalSabor = null
            }
        )
    }

    ventaPromoSabor?.let { sabor ->
        VentaPromocionDialog(
            sabor = sabor,
            promociones = promocionesActivasPara(promociones, sabor, fecha).map { it.promocion },
            onDismiss = { ventaPromoSabor = null },
            onConfirm = { promocion ->
                vm.registrarVentaPromocion(promocion, sabor)
                ventaPromoSabor = null
            }
        )
    }
    if (mostrarAgregarVenta) {
        VentaLineasDialog(
            titulo = "Agregar venta",
            fechaInicial = fecha,
            sabores = sabores,
            promociones = promociones,
            ventaInicial = null,
            onDismiss = { mostrarAgregarVenta = false },
            onGuardar = { fechaVenta, promocion, lineas ->
                if (promocion == null) {
                    vm.registrarVentaLineas(lineas, fechaVenta)
                } else {
                    vm.registrarVentaPromocionLineas(promocion, lineas, fechaVenta)
                }
                mostrarAgregarVenta = false
            }
        )
    }

    ventaEditando?.let { venta ->
        VentaLineasDialog(
            titulo = "Editar venta",
            fechaInicial = venta.venta.fechaVenta.aFechaLocal(),
            sabores = sabores,
            promociones = promociones,
            ventaInicial = venta,
            onDismiss = { ventaEditando = null },
            onGuardar = { fechaVenta, promocion, lineas ->
                vm.actualizarVenta(venta.venta, lineas, fechaVenta, promocion)
                ventaEditando = null
            }
        )
    }
}

@Composable
private fun InventarioScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val resumen by vm.saboresResumen.collectAsState()
    val sabores by vm.sabores.collectAsState()
    val movimientos by vm.movimientosRecientes.collectAsState()
    var busqueda by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("TODAS") }
    var mostrarInactivos by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<SaborEntity?>(null) }
    var nuevoSabor by remember { mutableStateOf(false) }
    var pedidoSabor by remember { mutableStateOf<SaborResumen?>(null) }
    var ajusteSabor by remember { mutableStateOf<SaborResumen?>(null) }
    var movimientoEditando by remember { mutableStateOf<MovimientoInventarioEntity?>(null) }

    val saboresPorId = remember(sabores) { sabores.associateBy { it.id } }
    val filtrados = remember(resumen, mostrarInactivos, categoria, busqueda) {
        resumen.filter {
            (mostrarInactivos || it.activo) &&
                (categoria == "TODAS" || it.categoria == categoria) &&
                it.nombre.contains(busqueda, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Inventario", style = MaterialTheme.typography.headlineSmall)
                    Text("Pedido sugerido por sabor", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { nuevoSabor = true }) { Text("Agregar") }
            }
        }
        item {
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                label = { Text("Buscar sabor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            CategoriaFiltro(categoria, onCategoria = { categoria = it })
        }
        item {
            FilterChip(
                selected = mostrarInactivos,
                onClick = { mostrarInactivos = !mostrarInactivos },
                label = { Text("Mostrar inactivos") }
            )
        }
        if (filtrados.isEmpty()) {
            item { EmptyState("Aún no hay sabores para mostrar.") }
        } else {
            items(filtrados, key = { it.id }) { sabor ->
                SaborInventarioCard(
                    sabor = sabor,
                    onEditar = { saboresPorId[sabor.id]?.let { editando = it } },
                    onPedido = { pedidoSabor = sabor },
                    onAjuste = { ajusteSabor = sabor }
                )
            }
        }
        if (movimientos.isNotEmpty()) {
            item {
                Text("Movimientos recientes", style = MaterialTheme.typography.titleMedium)
            }
            items(movimientos, key = { movimiento -> "mov-${movimiento.id}" }) { movimiento ->
                MovimientoInventarioCard(
                    movimiento = movimiento,
                    saborNombre = saboresPorId[movimiento.saborId]?.nombre ?: "Sabor inactivo",
                    onEditar = { movimientoEditando = movimiento }
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (nuevoSabor) {
        SaborDialog(
            titulo = "Agregar sabor",
            sabor = null,
            onDismiss = { nuevoSabor = false },
            onGuardarNuevo = { nombre, categoriaNueva, ideal, actual, precio ->
                vm.crearSabor(nombre, categoriaNueva, ideal, actual, precio)
                nuevoSabor = false
            },
            onGuardarEdicion = {}
        )
    }

    editando?.let { sabor ->
        SaborDialog(
            titulo = "Editar sabor",
            sabor = sabor,
            onDismiss = { editando = null },
            onGuardarNuevo = { _, _, _, _, _ -> },
            onGuardarEdicion = {
                vm.actualizarSabor(it)
                editando = null
            },
            onDesactivar = {
                vm.borrarODesactivarSabor(sabor.id)
                editando = null
            }
        )
    }

    pedidoSabor?.let { sabor ->
        CantidadDialog(
            titulo = "Pedido recibido",
            descripcion = "Sabor: ${sabor.nombre}. Pedido sugerido: ${sabor.pedidoSugerido.formatoUnidades()} bolsas.",
            etiquetaCantidad = "Bolsas recibidas",
            onDismiss = { pedidoSabor = null },
            onConfirm = { cantidad ->
                vm.registrarPedidoRecibido(sabor.id, cantidad)
                pedidoSabor = null
            }
        )
    }

    ajusteSabor?.let { sabor ->
        AjusteDialog(
            sabor = sabor,
            onDismiss = { ajusteSabor = null },
            onConfirm = { tipo, cantidad, motivo ->
                vm.registrarAjuste(sabor.id, tipo, cantidad, motivo)
                ajusteSabor = null
            }
        )
    }

    movimientoEditando?.let { movimiento ->
        MovimientoEditDialog(
            movimiento = movimiento,
            sabores = sabores.filter { it.activo },
            onDismiss = { movimientoEditando = null },
            onGuardar = { saborId, tipo, cantidad, fecha, motivo ->
                vm.actualizarMovimiento(movimiento.id, saborId, tipo, cantidad, fecha, motivo)
                movimientoEditando = null
            }
        )
    }
}

@Composable
private fun ConfiguracionScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val config by vm.configuracion.collectAsState()
    val sabores by vm.saboresActivos.collectAsState()
    val promociones by vm.promociones.collectAsState()
    var precioDulces by remember(config.precioBaseDulces) { mutableStateOf(config.precioBaseDulces.toString()) }
    var precioSaladas by remember(config.precioBaseSaladas) { mutableStateOf(config.precioBaseSaladas.toString()) }
    var nuevaPromo by remember { mutableStateOf(false) }
    var preciosMasivos by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Configuración", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Precios base", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = precioDulces,
                        onValueChange = { precioDulces = it.soloDecimal() },
                        label = { Text("Precio base dulces") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = precioSaladas,
                        onValueChange = { precioSaladas = it.soloDecimal() },
                        label = { Text("Precio base saladas") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            vm.guardarConfiguracion(
                                config.copy(
                                    precioBaseDulces = precioDulces.toDoubleOrNull() ?: config.precioBaseDulces,
                                    precioBaseSaladas = precioSaladas.toDoubleOrNull() ?: config.precioBaseSaladas
                                )
                            )
                        }) { Text("Guardar") }
                        OutlinedButton(onClick = { preciosMasivos = true }) { Text("Aplicar a sabores") }
                    }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Preferencias", style = MaterialTheme.typography.titleMedium)
                    Text("Alerta de inventario bajo: ${config.umbralInventarioBajo} bolsas o menos")
                    Text("Fecha: 09/Junio/26")
                    Text("Dinero: ${1234.56.formatoDinero()}")
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Promociones", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { nuevaPromo = true }) { Text("Agregar") }
            }
        }
        if (promociones.isEmpty()) {
            item { EmptyState("No hay promociones registradas.") }
        } else {
            items(promociones, key = { it.promocion.id }) { promo ->
                PromocionCard(
                    promocion = promo,
                    onDesactivar = { vm.desactivarPromocion(promo.promocion.id) }
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (nuevaPromo) {
        PromocionDialog(
            sabores = sabores,
            onDismiss = { nuevaPromo = false },
            onGuardar = { nombre, cantidad, precio, inicio, fin, saborIds ->
                vm.crearPromocion(nombre, cantidad, precio, inicio, fin, saborIds)
                nuevaPromo = false
            }
        )
    }

    if (preciosMasivos) {
        PreciosMasivosDialog(
            sabores = sabores,
            precioDulces = precioDulces.toDoubleOrNull() ?: config.precioBaseDulces,
            precioSaladas = precioSaladas.toDoubleOrNull() ?: config.precioBaseSaladas,
            onDismiss = { preciosMasivos = false },
            onAplicarSeleccion = { ids, precio ->
                vm.aplicarPrecioASeleccion(ids, precio)
                preciosMasivos = false
            },
            onAplicarCategorias = { categorias, precio ->
                vm.aplicarPrecioACategorias(categorias, precio)
                preciosMasivos = false
            }
        )
    }
}

@Composable
private fun ReportesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val fecha by vm.fechaSeleccionada.collectAsState()
    val tipo by vm.pantallaReportes.collectAsState()
    val inicioRango by vm.inicioRango.collectAsState()
    val finRango by vm.finRango.collectAsState()
    val reporte by vm.reporteActual.collectAsState()
    var elegirInicio by remember { mutableStateOf(false) }
    var elegirFin by remember { mutableStateOf(false) }
    var elegirMes by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Reportes", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoReporte.entries.forEach { item ->
                    FilterChip(
                        selected = tipo == item,
                        onClick = { vm.pantallaReportes.value = item },
                        label = { Text(item.titulo()) }
                    )
                }
            }
        }
        item {
            FechaHeader(
                fecha = fecha,
                onAnterior = { vm.cambiarFecha(if (tipo == TipoReporte.SEMANA) -7L else -1L) },
                onSiguiente = { vm.cambiarFecha(if (tipo == TipoReporte.SEMANA) 7L else 1L) },
                onHoy = { vm.hoy() },
                onSeleccionarFecha = { vm.seleccionarFecha(it) }
            )
        }
        if (tipo == TipoReporte.MES) {
            item {
                OutlinedButton(
                    onClick = { elegirMes = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Elegir mes y año") }
            }
        }
        if (tipo == TipoReporte.RANGO) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Rango de fechas", fontWeight = FontWeight.SemiBold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { vm.inicioRango.value = inicioRango.minusDays(1) }) { Text("<") }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Inicio")
                                Text(inicioRango.formatoFechaNegocio(), fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { elegirInicio = true }) { Text("Elegir") }
                            }
                            OutlinedButton(onClick = { vm.inicioRango.value = inicioRango.plusDays(1) }) { Text(">") }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { vm.finRango.value = finRango.minusDays(1) }) { Text("<") }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Fin")
                                Text(finRango.formatoFechaNegocio(), fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { elegirFin = true }) { Text("Elegir") }
                            }
                            OutlinedButton(onClick = { vm.finRango.value = finRango.plusDays(1) }) { Text(">") }
                        }
                    }
                }
            }
        }
        item {
            ResumenVentasCard(
                totalUnidades = reporte.totalUnidades,
                totalDinero = reporte.totalDinero,
                pedidoSugeridoTotal = reporte.pedidoSugeridoTotal
            )
        }
        item {
            Text("Sabores más vendidos", style = MaterialTheme.typography.titleMedium)
        }
        if (reporte.saboresMasVendidos.isEmpty()) {
            item { EmptyState("No hay ventas en este periodo.") }
        } else {
            items(reporte.saboresMasVendidos, key = { it.saborId }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(item.nombre, fontWeight = FontWeight.SemiBold)
                            Text("${item.unidades.formatoUnidades()} bolsas")
                        }
                        Text(item.dinero.formatoDinero(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (elegirInicio) {
        FechaPickerDialog(
            fechaInicial = inicioRango,
            onDismiss = { elegirInicio = false },
            onConfirm = {
                vm.inicioRango.value = it
                elegirInicio = false
            }
        )
    }
    if (elegirFin) {
        FechaPickerDialog(
            fechaInicial = finRango,
            onDismiss = { elegirFin = false },
            onConfirm = {
                vm.finRango.value = it
                elegirFin = false
            }
        )
    }
    if (elegirMes) {
        MesAnioDialog(
            fechaInicial = fecha,
            onDismiss = { elegirMes = false },
            onConfirm = {
                vm.seleccionarFecha(it.withDayOfMonth(1))
                elegirMes = false
            }
        )
    }

}

@Composable
private fun FechaHeader(
    fecha: LocalDate,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
    onHoy: () -> Unit,
    onSeleccionarFecha: (LocalDate) -> Unit
) {
    var mostrarCalendario by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onAnterior) { Text("<") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fecha.formatoFechaNegocio(), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { mostrarCalendario = true }) { Text("Elegir") }
                    TextButton(onClick = onHoy) { Text("Hoy") }
                }
            }
            OutlinedButton(onClick = onSiguiente) { Text(">") }
        }
    }
    if (mostrarCalendario) {
        FechaPickerDialog(
            fechaInicial = fecha,
            onDismiss = { mostrarCalendario = false },
            onConfirm = {
                onSeleccionarFecha(it)
                mostrarCalendario = false
            }
        )
    }

}

@Composable
private fun ResumenVentasCard(totalUnidades: Int, totalDinero: Double, pedidoSugeridoTotal: Int) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resumen", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ResumenDato("Bolsas vendidas", totalUnidades.formatoUnidades())
                ResumenDato("Dinero vendido", totalDinero.formatoDinero())
            }
            ResumenDato("Pedido sugerido total", "${pedidoSugeridoTotal.formatoUnidades()} bolsas")
        }
    }
}

@Composable
private fun ResumenDato(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
        Text(valor, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoriaFiltro(categoria: String, onCategoria: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = categoria == "TODAS", onClick = { onCategoria("TODAS") }, label = { Text("Todas") })
        FilterChip(selected = categoria == CategoriaSabor.DULCE, onClick = { onCategoria(CategoriaSabor.DULCE) }, label = { Text("Dulces") })
        FilterChip(selected = categoria == CategoriaSabor.SALADA, onClick = { onCategoria(CategoriaSabor.SALADA) }, label = { Text("Saladas") })
    }
}

@Composable
private fun SaborVentaCard(
    sabor: SaborEntity,
    resumen: SaborResumen?,
    promociones: List<PromocionConSabores>,
    onVenta: () -> Unit,
    onPromo: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(sabor.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(sabor.categoria.nombreCategoria(), style = MaterialTheme.typography.bodySmall)
                }
                Text(sabor.precioVenta.formatoDinero(), fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("Actual: ${sabor.inventarioActual.formatoUnidades()}") })
                AssistChip(onClick = {}, label = { Text("Ideal: ${sabor.inventarioIdeal.formatoUnidades()}") })
                if (resumen?.bajoInventario == true) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Inventario bajo") },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onVenta, modifier = Modifier.weight(1f)) { Text("Venta") }
                OutlinedButton(
                    onClick = onPromo,
                    enabled = promociones.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Promoción") }
            }
        }
    }
}

@Composable
private fun SaborInventarioCard(
    sabor: SaborResumen,
    onEditar: () -> Unit,
    onPedido: () -> Unit,
    onAjuste: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(sabor.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${sabor.categoria.nombreCategoria()} - ${if (sabor.activo) "Activo" else "Inactivo"}")
                }
                Text(sabor.precioVenta.formatoDinero(), fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("Actual: ${sabor.inventarioActual.formatoUnidades()}") })
                AssistChip(onClick = {}, label = { Text("Ideal: ${sabor.inventarioIdeal.formatoUnidades()}") })
                AssistChip(onClick = {}, label = { Text("Pedido sugerido: ${sabor.pedidoSugerido.formatoUnidades()}") })
                if (sabor.bajoInventario) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Inventario bajo") },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEditar, modifier = Modifier.weight(1f)) { Text("Editar") }
                Button(onClick = onPedido, modifier = Modifier.weight(1f)) { Text("Pedido recibido") }
            }
            OutlinedButton(onClick = onAjuste, modifier = Modifier.fillMaxWidth()) { Text("Ajustar inventario") }
        }
    }
}

@Composable
private fun MovimientoInventarioCard(
    movimiento: MovimientoInventarioEntity,
    saborNombre: String,
    onEditar: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(saborNombre, fontWeight = FontWeight.SemiBold)
                    Text(movimiento.tipo.nombreMovimiento(), style = MaterialTheme.typography.bodySmall)
                }
                Text("${movimiento.cantidad.formatoUnidades()} bolsas", fontWeight = FontWeight.Bold)
            }
            Text(movimiento.fechaMovimiento.aFechaLocal().formatoFechaNegocio(), style = MaterialTheme.typography.bodySmall)
            movimiento.motivo?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onEditar) { Text("Editar movimiento") }
        }
    }
}

@Composable
private fun EmptyState(texto: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(
            texto,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        )
    }
}

@Composable
private fun VentaLineasDialog(
    titulo: String,
    fechaInicial: LocalDate,
    sabores: List<SaborEntity>,
    promociones: List<PromocionConSabores>,
    ventaInicial: VentaConDetalles?,
    onDismiss: () -> Unit,
    onGuardar: (LocalDate, PromocionEntity?, List<VentaLineaInput>) -> Unit
) {
    var fechaVenta by remember { mutableStateOf(fechaInicial) }
    var mostrarCalendario by remember { mutableStateOf(false) }
    var esPromocion by remember { mutableStateOf(ventaInicial?.venta?.promocionId != null) }
    var promocionSeleccionada by remember {
        mutableStateOf(promociones.firstOrNull { it.promocion.id == ventaInicial?.venta?.promocionId })
    }
    var selectorLineaId by remember { mutableStateOf<Int?>(null) }
    val lineasIniciales = (ventaInicial?.detalles?.size ?: 1).coerceAtLeast(1)
    var siguienteId by remember(ventaInicial) { mutableStateOf(lineasIniciales + 1) }
    val lineas = remember(ventaInicial, sabores) {
        mutableStateListOf<VentaLineaUi>().apply {
            if (ventaInicial != null) {
                ventaInicial.detalles.forEachIndexed { index, detalle ->
                    add(VentaLineaUi(index + 1, detalle.saborId, detalle.cantidad.toString()))
                }
            }
            if (isEmpty()) add(VentaLineaUi(1, null, "1"))
        }
    }
    val promo = if (esPromocion) promocionSeleccionada?.promocion else null
    val saboresPermitidos = remember(esPromocion, promocionSeleccionada, sabores) {
        val permitidos = promocionSeleccionada?.sabores.orEmpty()
        when {
            !esPromocion -> sabores
            permitidos.isEmpty() -> sabores
            else -> sabores.filter { sabor -> permitidos.any { it.id == sabor.id } }
        }
    }
    val totalLineas = lineas.sumOf { it.cantidad.toIntOrNull() ?: 0 }
    val puedeGuardar = lineas.isNotEmpty() &&
        lineas.all { it.saborId != null && (it.cantidad.toIntOrNull() ?: 0) > 0 } &&
        (!esPromocion || (promo != null && totalLineas == promo.cantidadUnidades))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Fecha", style = MaterialTheme.typography.bodySmall)
                            Text(fechaVenta.formatoFechaNegocio(), fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = { mostrarCalendario = true }) { Text("Elegir") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !esPromocion,
                        onClick = {
                            esPromocion = false
                            promocionSeleccionada = null
                        },
                        label = { Text("Venta por pieza") }
                    )
                    FilterChip(
                        selected = esPromocion,
                        onClick = { esPromocion = true },
                        label = { Text("Venta por promoción") }
                    )
                }
                if (esPromocion) {
                    val activas = promociones.filter { it.promocion.activa }
                    Text("Promoción", fontWeight = FontWeight.SemiBold)
                    if (activas.isEmpty()) {
                        Text("No hay promociones activas.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            activas.forEach { promoConSabores ->
                                FilterChip(
                                    selected = promocionSeleccionada?.promocion?.id == promoConSabores.promocion.id,
                                    onClick = { promocionSeleccionada = promoConSabores },
                                    label = { Text("${promoConSabores.promocion.nombre} (${promoConSabores.promocion.cantidadUnidades} bolsas)") }
                                )
                            }
                        }
                    }
                    promo?.let {
                        Text(
                            "Selecciona ${it.cantidadUnidades.formatoUnidades()} bolsas. Actual: ${totalLineas.formatoUnidades()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text("Sabores vendidos", fontWeight = FontWeight.SemiBold)
                lineas.forEachIndexed { index, linea ->
                    val sabor = sabores.firstOrNull { it.id == linea.saborId }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(sabor?.nombre ?: "Elige sabor", fontWeight = FontWeight.SemiBold)
                                    Text(sabor?.categoria?.nombreCategoria() ?: "Sin seleccionar", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { selectorLineaId = linea.id }) { Text("Cambiar") }
                            }
                            OutlinedTextField(
                                value = linea.cantidad,
                                onValueChange = { nueva ->
                                    lineas[index] = linea.copy(cantidad = nueva.soloEntero())
                                },
                                label = { Text("Bolsas") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (lineas.size > 1) {
                                TextButton(onClick = { lineas.removeAt(index) }) { Text("Quitar línea") }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { lineas.add(VentaLineaUi(siguienteId++, null, "1")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Agregar sabor") }
            }
        },
        confirmButton = {
            Button(
                enabled = puedeGuardar,
                onClick = {
                    val inputs = lineas.mapNotNull { linea ->
                        val sabor = sabores.firstOrNull { it.id == linea.saborId }
                        val cantidad = linea.cantidad.toIntOrNull()
                        if (sabor != null && cantidad != null && cantidad > 0) {
                            VentaLineaInput(sabor = sabor, cantidad = cantidad)
                        } else {
                            null
                        }
                    }
                    onGuardar(fechaVenta, promo, inputs)
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (mostrarCalendario) {
        FechaPickerDialog(
            fechaInicial = fechaVenta,
            onDismiss = { mostrarCalendario = false },
            onConfirm = {
                fechaVenta = it
                mostrarCalendario = false
            }
        )
    }

    selectorLineaId?.let { lineaId ->
        SaborSelectorDialog(
            sabores = saboresPermitidos,
            onDismiss = { selectorLineaId = null },
            onSeleccionar = { sabor ->
                val index = lineas.indexOfFirst { it.id == lineaId }
                if (index >= 0) lineas[index] = lineas[index].copy(saborId = sabor.id)
                selectorLineaId = null
            }
        )
    }

}

@Composable
private fun SaborSelectorDialog(
    sabores: List<SaborEntity>,
    onDismiss: () -> Unit,
    onSeleccionar: (SaborEntity) -> Unit
) {
    var busqueda by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("TODAS") }
    val filtrados = remember(sabores, busqueda, categoria) {
        sabores.filter {
            (categoria == "TODAS" || it.categoria == categoria) &&
                it.nombre.contains(busqueda, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir sabor") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it },
                    label = { Text("Buscar sabor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CategoriaFiltro(categoria) { categoria = it }
                filtrados.forEach { sabor ->
                    OutlinedButton(
                        onClick = { onSeleccionar(sabor) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${sabor.nombre} - ${sabor.categoria.nombreCategoria()}")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun VentaNormalDialog(
    sabor: SaborEntity,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var cantidad by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Venta normal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(sabor.nombre)
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { cantidad = it.soloEntero() },
                    label = { Text("Bolsas vendidas") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(cantidad.toIntOrNull()?.coerceAtLeast(1) ?: 1) }
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun VentaPromocionDialog(
    sabor: SaborEntity,
    promociones: List<PromocionEntity>,
    onDismiss: () -> Unit,
    onConfirm: (PromocionEntity) -> Unit
) {
    var seleccion by remember { mutableStateOf(promociones.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Venta con promoción") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(sabor.nombre)
                promociones.forEach { promo ->
                    FilterChip(
                        selected = seleccion?.id == promo.id,
                        onClick = { seleccion = promo },
                        label = { Text("${promo.nombre} - ${promo.cantidadUnidades} bolsas - ${promo.precioPromocional.formatoDinero()}") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = seleccion != null,
                onClick = { seleccion?.let(onConfirm) }
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SaborDialog(
    titulo: String,
    sabor: SaborEntity?,
    onDismiss: () -> Unit,
    onGuardarNuevo: (String, String, Int, Int, Double?) -> Unit,
    onGuardarEdicion: (SaborEntity) -> Unit,
    onDesactivar: (() -> Unit)? = null
) {
    var nombre by remember { mutableStateOf(sabor?.nombre ?: "") }
    var categoria by remember { mutableStateOf(sabor?.categoria ?: CategoriaSabor.DULCE) }
    var ideal by remember { mutableStateOf((sabor?.inventarioIdeal ?: 32).toString()) }
    var actual by remember { mutableStateOf((sabor?.inventarioActual ?: 0).toString()) }
    var precio by remember { mutableStateOf(sabor?.precioVenta?.toString() ?: "") }
    val puedeGuardar = nombre.isNotBlank() && ideal.toIntOrNull() != null && actual.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                CategoriaFiltro(categoria) { if (it != "TODAS") categoria = it }
                OutlinedTextField(value = ideal, onValueChange = { ideal = it.soloEntero() }, label = { Text("Inventario ideal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = actual, onValueChange = { actual = it.soloEntero() }, label = { Text("Inventario actual") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = precio, onValueChange = { precio = it.soloDecimal() }, label = { Text("Precio personalizado opcional") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text("Si dejas el precio vacío al crear un sabor, usará el precio base de su categoría.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = puedeGuardar,
                onClick = {
                    val idealInt = ideal.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val actualInt = actual.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val precioDouble = precio.toDoubleOrNull()?.takeIf { it >= 0.0 }
                    if (sabor == null) {
                        onGuardarNuevo(nombre, categoria, idealInt, actualInt, precioDouble)
                    } else {
                        onGuardarEdicion(
                            sabor.copy(
                                nombre = nombre,
                                categoria = categoria,
                                inventarioIdeal = idealInt,
                                inventarioActual = actualInt,
                                precioVenta = precioDouble ?: sabor.precioVenta,
                                precioPersonalizado = precioDouble != null
                            )
                        )
                    }
                }
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                onDesactivar?.let {
                    TextButton(onClick = it) { Text("Desactivar") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun CantidadDialog(
    titulo: String,
    descripcion: String,
    etiquetaCantidad: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var cantidad by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(descripcion)
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { cantidad = it.soloEntero() },
                    label = { Text(etiquetaCantidad) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                enabled = (cantidad.toIntOrNull() ?: 0) > 0,
                onClick = { onConfirm(cantidad.toIntOrNull() ?: 0) }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AjusteDialog(
    sabor: SaborResumen,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String) -> Unit
) {
    val opciones = listOf(
        TipoMovimiento.REGALO_PROVEEDOR to "Regalo de proveedor",
        TipoMovimiento.MERMA_DANADO to "Merma / dañado",
        TipoMovimiento.CORTESIA_CLIENTE to "Cortesía / regalo al cliente",
        TipoMovimiento.CORRECCION_CONTEO to "Corrección de conteo",
        TipoMovimiento.OTRO_AJUSTE to "Otro ajuste"
    )
    var tipo by remember { mutableStateOf(opciones.first().first) }
    var cantidad by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }
    var restarManual by remember { mutableStateOf(false) }
    val permiteElegirSigno = tipo == TipoMovimiento.CORRECCION_CONTEO || tipo == TipoMovimiento.OTRO_AJUSTE
    val signo = when {
        tipo == TipoMovimiento.MERMA_DANADO || tipo == TipoMovimiento.CORTESIA_CLIENTE -> -1
        permiteElegirSigno && restarManual -> -1
        else -> 1
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar inventario") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(sabor.nombre)
                opciones.forEach { opcion ->
                    FilterChip(
                        selected = tipo == opcion.first,
                        onClick = {
                            tipo = opcion.first
                            restarManual = false
                        },
                        label = { Text(opcion.second) }
                    )
                }
                if (permiteElegirSigno) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = restarManual, onCheckedChange = { restarManual = it })
                        Text("Restar del inventario")
                    }
                }
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { cantidad = it.soloEntero() },
                    label = { Text("Bolsas") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(value = motivo, onValueChange = { motivo = it }, label = { Text("Nota opcional") })
            }
        },
        confirmButton = {
            Button(
                enabled = (cantidad.toIntOrNull() ?: 0) > 0,
                onClick = {
                    onConfirm(tipo, (cantidad.toIntOrNull() ?: 0) * signo, motivo)
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MovimientoEditDialog(
    movimiento: MovimientoInventarioEntity,
    sabores: List<SaborEntity>,
    onDismiss: () -> Unit,
    onGuardar: (Long, String, Int, LocalDate, String) -> Unit
) {
    val opciones = listOf(
        TipoMovimiento.PEDIDO_RECIBIDO to "Pedido recibido",
        TipoMovimiento.REGALO_PROVEEDOR to "Regalo de proveedor",
        TipoMovimiento.MERMA_DANADO to "Merma / dañado",
        TipoMovimiento.CORTESIA_CLIENTE to "Cortesía / regalo al cliente",
        TipoMovimiento.CORRECCION_CONTEO to "Corrección de conteo",
        TipoMovimiento.OTRO_AJUSTE to "Otro ajuste"
    )
    var saborId by remember { mutableStateOf(movimiento.saborId) }
    var tipo by remember { mutableStateOf(movimiento.tipo) }
    var cantidad by remember { mutableStateOf(kotlin.math.abs(movimiento.cantidad).toString()) }
    var fecha by remember { mutableStateOf(movimiento.fechaMovimiento.aFechaLocal()) }
    var motivo by remember { mutableStateOf(movimiento.motivo.orEmpty()) }
    var restarManual by remember { mutableStateOf(movimiento.cantidad < 0) }
    var elegirSabor by remember { mutableStateOf(false) }
    var elegirFecha by remember { mutableStateOf(false) }
    val permiteElegirSigno = tipo == TipoMovimiento.CORRECCION_CONTEO || tipo == TipoMovimiento.OTRO_AJUSTE
    val signo = when {
        tipo == TipoMovimiento.MERMA_DANADO || tipo == TipoMovimiento.CORTESIA_CLIENTE -> -1
        permiteElegirSigno && restarManual -> -1
        else -> 1
    }
    val sabor = sabores.firstOrNull { it.id == saborId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar movimiento") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sabor?.nombre ?: "Elige sabor", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { elegirSabor = true }) { Text("Cambiar") }
                    }
                }
                FechaCompacta(
                    titulo = "Fecha",
                    fecha = fecha,
                    onAnterior = { fecha = fecha.minusDays(1) },
                    onSiguiente = { fecha = fecha.plusDays(1) }
                )
                TextButton(onClick = { elegirFecha = true }) { Text("Elegir fecha") }
                opciones.forEach { opcion ->
                    FilterChip(
                        selected = tipo == opcion.first,
                        onClick = {
                            tipo = opcion.first
                            restarManual = false
                        },
                        label = { Text(opcion.second) }
                    )
                }
                if (permiteElegirSigno) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = restarManual, onCheckedChange = { restarManual = it })
                        Text("Restar del inventario")
                    }
                }
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { cantidad = it.soloEntero() },
                    label = { Text("Bolsas") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text("Nota opcional") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = saborId != 0L && (cantidad.toIntOrNull() ?: 0) > 0,
                onClick = { onGuardar(saborId, tipo, (cantidad.toIntOrNull() ?: 0) * signo, fecha, motivo) }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (elegirSabor) {
        SaborSelectorDialog(
            sabores = sabores,
            onDismiss = { elegirSabor = false },
            onSeleccionar = {
                saborId = it.id
                elegirSabor = false
            }
        )
    }
    if (elegirFecha) {
        FechaPickerDialog(
            fechaInicial = fecha,
            onDismiss = { elegirFecha = false },
            onConfirm = {
                fecha = it
                elegirFecha = false
            }
        )
    }

}

@Composable
private fun PromocionDialog(
    sabores: List<SaborEntity>,
    onDismiss: () -> Unit,
    onGuardar: (String, Int, Double, LocalDate, LocalDate?, List<Long>) -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("3") }
    var precio by remember { mutableStateOf("") }
    var fechaInicio by remember { mutableStateOf(LocalDate.now()) }
    var fechaFin by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var sinFechaFin by remember { mutableStateOf(true) }
    val seleccion = remember { mutableStateListOf<Long>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar promoción") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(value = cantidad, onValueChange = { cantidad = it.soloEntero() }, label = { Text("Bolsas incluidas") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = precio, onValueChange = { precio = it.soloDecimal() }, label = { Text("Precio promocional") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                FechaCompacta(
                    titulo = "Inicio",
                    fecha = fechaInicio,
                    onAnterior = { fechaInicio = fechaInicio.minusDays(1) },
                    onSiguiente = { fechaInicio = fechaInicio.plusDays(1) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sinFechaFin, onCheckedChange = { sinFechaFin = it })
                    Text("Sin fecha de fin")
                }
                if (!sinFechaFin) {
                    FechaCompacta(
                        titulo = "Fin",
                        fecha = fechaFin,
                        onAnterior = { fechaFin = fechaFin.minusDays(1) },
                        onSiguiente = { fechaFin = fechaFin.plusDays(1) }
                    )
                }
                Text("Sabores incluidos. Si no seleccionas ninguno, aplica a todos.", style = MaterialTheme.typography.bodySmall)
                sabores.forEach { sabor ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = seleccion.contains(sabor.id),
                            onCheckedChange = {
                                if (it) seleccion.add(sabor.id) else seleccion.remove(sabor.id)
                            }
                        )
                        Text("${sabor.nombre} (${sabor.categoria.nombreCategoria()})")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = nombre.isNotBlank() && (cantidad.toIntOrNull() ?: 0) > 0 && (precio.toDoubleOrNull() ?: -1.0) >= 0.0,
                onClick = {
                    onGuardar(
                        nombre,
                        cantidad.toIntOrNull() ?: 1,
                        precio.toDoubleOrNull() ?: 0.0,
                        fechaInicio,
                        if (sinFechaFin) null else fechaFin,
                        seleccion.toList()
                    )
                }
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun FechaCompacta(
    titulo: String,
    fecha: LocalDate,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onAnterior) { Text("<") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(titulo, style = MaterialTheme.typography.bodySmall)
                Text(fecha.formatoFechaNegocio(), fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = onSiguiente) { Text(">") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FechaPickerDialog(
    fechaInicial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = fechaInicial.aMillisLocales())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(it.aFechaLocal()) }
                }
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun MesAnioDialog(
    fechaInicial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val meses = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    var anio by remember { mutableStateOf(fechaInicial.year.coerceAtLeast(2026)) }
    var mes by remember { mutableStateOf(fechaInicial.monthValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir mes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { if (anio > 2026) anio-- }) { Text("<") }
                    Text(anio.toString(), fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { anio++ }) { Text(">") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meses.forEachIndexed { index, nombre ->
                        FilterChip(
                            selected = mes == index + 1,
                            onClick = { mes = index + 1 },
                            label = { Text(nombre) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(LocalDate.of(anio, mes, 1)) }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun LocalDate.aMillisLocales(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.aFechaLocal(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun PromocionCard(promocion: PromocionConSabores, onDesactivar: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(promocion.promocion.nombre, fontWeight = FontWeight.SemiBold)
                Text(if (promocion.promocion.activa) "Activa" else "Inactiva")
            }
            Text("${promocion.promocion.cantidadUnidades} bolsas por ${promocion.promocion.precioPromocional.formatoDinero()}")
            Text(
                if (promocion.sabores.isEmpty()) "Aplica a todos los sabores"
                else "Sabores: ${promocion.sabores.joinToString { it.nombre }}"
            )
            if (promocion.promocion.activa) {
                OutlinedButton(onClick = onDesactivar) { Text("Desactivar") }
            }
        }
    }
}

@Composable
private fun PreciosMasivosDialog(
    sabores: List<SaborEntity>,
    precioDulces: Double,
    precioSaladas: Double,
    onDismiss: () -> Unit,
    onAplicarSeleccion: (List<Long>, Double) -> Unit,
    onAplicarCategorias: (List<String>, Double) -> Unit
) {
    var precio by remember { mutableStateOf("") }
    var aplicarDulces by remember { mutableStateOf(false) }
    var aplicarSaladas by remember { mutableStateOf(false) }
    val seleccion = remember { mutableStateListOf<Long>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aplicar precios") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Puedes aplicar a una categoría completa o solo a sabores seleccionados.")
                OutlinedTextField(
                    value = precio,
                    onValueChange = { precio = it.soloDecimal() },
                    label = { Text("Precio a aplicar") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = aplicarDulces, onCheckedChange = { aplicarDulces = it })
                    Text("Todos los dulces (${precioDulces.formatoDinero()})")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = aplicarSaladas, onCheckedChange = { aplicarSaladas = it })
                    Text("Todas las saladas (${precioSaladas.formatoDinero()})")
                }
                Divider()
                sabores.forEach { sabor ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = seleccion.contains(sabor.id),
                            onCheckedChange = {
                                if (it) seleccion.add(sabor.id) else seleccion.remove(sabor.id)
                            }
                        )
                        Text("${sabor.nombre} - ${sabor.categoria.nombreCategoria()}")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = (precio.toDoubleOrNull() ?: -1.0) >= 0.0,
                onClick = {
                    val precioFinal = precio.toDoubleOrNull() ?: 0.0
                    val categorias = buildList {
                        if (aplicarDulces) add(CategoriaSabor.DULCE)
                        if (aplicarSaladas) add(CategoriaSabor.SALADA)
                    }
                    if (categorias.isNotEmpty()) onAplicarCategorias(categorias, precioFinal)
                    if (seleccion.isNotEmpty()) onAplicarSeleccion(seleccion.toList(), precioFinal)
                }
            ) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun promocionesActivasPara(
    promociones: List<PromocionConSabores>,
    sabor: SaborEntity,
    fecha: LocalDate
): List<PromocionConSabores> {
    val fechaMillis = fecha.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    return promociones.filter { promo ->
        promo.promocion.activa &&
            promo.promocion.fechaInicio <= fechaMillis &&
            (promo.promocion.fechaFin == null || promo.promocion.fechaFin >= fechaMillis) &&
            (promo.sabores.isEmpty() || promo.sabores.any { it.id == sabor.id })
    }
}

private fun String.nombreCategoria(): String =
    if (this == CategoriaSabor.DULCE) "Dulces" else "Saladas"

private fun String.nombreMovimiento(): String =
    when (this) {
        TipoMovimiento.VENTA -> "Venta"
        TipoMovimiento.PEDIDO_RECIBIDO -> "Pedido recibido"
        TipoMovimiento.REGALO_PROVEEDOR -> "Regalo de proveedor"
        TipoMovimiento.MERMA_DANADO -> "Merma / dañado"
        TipoMovimiento.CORTESIA_CLIENTE -> "Cortesía / regalo al cliente"
        TipoMovimiento.CORRECCION_CONTEO -> "Corrección de conteo"
        else -> "Otro ajuste"
    }

private fun TipoReporte.titulo(): String =
    when (this) {
        TipoReporte.DIA -> "Día"
        TipoReporte.SEMANA -> "Semana"
        TipoReporte.MES -> "Mes"
        TipoReporte.RANGO -> "Rango"
    }

private fun String.soloEntero(): String = filter { it.isDigit() }

private fun String.soloDecimal(): String {
    var punto = false
    return filter {
        when {
            it.isDigit() -> true
            it == '.' && !punto -> {
                punto = true
                true
            }
            else -> false
        }
    }
}
