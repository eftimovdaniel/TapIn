package com.tapin.teacher.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapin.teacher.data.api.CourseView
import com.tapin.teacher.ui.CoursesViewModel
import com.tapin.teacher.ui.Ink
import com.tapin.teacher.ui.Ink10
import com.tapin.teacher.ui.Ink20
import com.tapin.teacher.ui.Ink40
import com.tapin.teacher.ui.Ink60
import com.tapin.teacher.ui.Paper

// ekran so lista na predmeti i kopche za kreiranje nov predmet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    onBack: () -> Unit,
    onCourseSelected: (CourseView) -> Unit,
    vm: CoursesViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // dali e otvoren dijalogot za kreiranje nov predmet
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Предмети", style = MaterialTheme.typography.titleMedium, color = Ink) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад", tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Paper)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                containerColor = Ink,
                contentColor = Paper,
                onClick = { showCreate = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Нов предмет") }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // prikazhi spinner / greshka / prazno / lista spored sostojbata
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Ink)
                }
                state.error != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Грешка при вчитување", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(state.error ?: "?", color = Ink40, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = vm::refresh) { Text("Обиди се повторно") }
                }
                state.items.isEmpty() -> EmptyCourses()
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.id }) { course ->
                        CourseRow(course = course, onClick = { onCourseSelected(course) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showCreate) {
        CreateCourseDialog(
            busy = state.createBusy,
            error = state.createError,
            onDismiss = {
                showCreate = false
                vm.clearCreateError()
            },
            onCreate = { code, name ->
                vm.createCourse(code, name) {
                    showCreate = false
                }
            }
        )
    }
}

// eden red vo listata na predmeti — klikaj za da otvorish sesija
@Composable
private fun CourseRow(course: CourseView, onClick: () -> Unit) {
    Surface(
        color = Ink10,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    course.code.firstOrNull()?.uppercase() ?: "?",
                    color = Paper,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(course.code, color = Ink40, style = MaterialTheme.typography.labelSmall)
                Text(course.name, color = Ink, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ink60)
        }
    }
}

// prazna sostojba koga ushte nema predmeti
@Composable
private fun EmptyCourses() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Сè уште немаш предмети", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Притисни „Нов предмет“ за да креираш прв предмет.",
            color = Ink40, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// dijalog so polinja za shifra i ime na nov predmet
@Composable
private fun CreateCourseDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Paper,
        titleContentColor = Ink,
        textContentColor = Ink60,
        title = { Text("Нов предмет") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Шифра (на пр. FCSE-MA101)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Ink, unfocusedBorderColor = Ink20,
                        focusedLabelColor = Ink, cursorColor = Ink
                    )
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Име на предмет") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Ink, unfocusedBorderColor = Ink20,
                        focusedLabelColor = Ink, cursorColor = Ink
                    )
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(code, name) },
                enabled = !busy && code.isNotBlank() && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
            ) {
                if (busy) CircularProgressIndicator(
                    Modifier.size(16.dp), color = Paper, strokeWidth = 2.dp
                )
                else Text("Креирај")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Откажи", color = Ink60)
            }
        }
    )
}
