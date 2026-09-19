package com.matedroid.ui.screens.settings.sections

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.backup.BackupCar
import com.matedroid.data.backup.BackupCounts
import com.matedroid.data.backup.BackupExporter
import com.matedroid.data.backup.BackupImporter
import com.matedroid.data.backup.BackupPreview
import com.matedroid.data.backup.BackupSection
import com.matedroid.data.backup.ImportMode
import com.matedroid.data.backup.ImportReport
import com.matedroid.data.backup.NewerBackupException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Why a backup could not be saved or read, for the screen to put into words. */
enum class BackupError {
    EXPORT_FAILED,
    NOT_A_BACKUP,
    BACKUP_TOO_NEW,
    RESTORE_FAILED
}

data class BackupUiState(
    val cars: List<BackupCar> = emptyList(),
    val exportCarIds: Set<Int> = emptySet(),
    val counts: BackupCounts = BackupCounts(),
    val exportSelection: Set<BackupSection> = BackupSection.exportDefaults,
    val isExporting: Boolean = false,
    val isReadingFile: Boolean = false,
    val isRestoring: Boolean = false,
    /** Set when a file is ready to hand to the share sheet; cleared once it has been. */
    val fileToShare: Uri? = null,
    val preview: BackupPreview? = null,
    val importSelection: Set<BackupSection> = emptySet(),
    val importCarIds: Set<Int> = emptySet(),
    val importMode: ImportMode = ImportMode.MERGE,
    val report: ImportReport? = null,
    val error: BackupError? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: BackupExporter,
    private val importer: BackupImporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /** The staged file being previewed, kept between "what is in it" and "restore it". */
    private var stagedImport: File? = null

    init {
        viewModelScope.launch {
            val cars = exporter.cars()
            val carIds = cars.map { it.carId }.toSet()
            _uiState.value = _uiState.value.copy(
                cars = cars,
                exportCarIds = carIds,
                counts = exporter.counts(carIds)
            )
        }
    }

    fun toggleExportSection(section: BackupSection) {
        if (section.alwaysIncluded) return
        val current = _uiState.value.exportSelection
        _uiState.value = _uiState.value.copy(
            exportSelection = if (section in current) current - section else current + section
        )
    }

    /** Ticking a car off drops its share of every count the export screen shows. */
    fun toggleExportCar(carId: Int) {
        val current = _uiState.value.exportCarIds
        val updated = if (carId in current) current - carId else current + carId
        _uiState.value = _uiState.value.copy(exportCarIds = updated)
        viewModelScope.launch {
            val counts = exporter.counts(updated)
            if (_uiState.value.exportCarIds == updated) {
                _uiState.value = _uiState.value.copy(counts = counts)
            }
        }
    }

    fun export() {
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(isExporting = true, error = null)
            _uiState.value = try {
                val file = exporter.export(state.exportSelection, state.exportCarIds)
                _uiState.value.copy(isExporting = false, fileToShare = file.toShareUri())
            } catch (e: Exception) {
                _uiState.value.copy(isExporting = false, error = BackupError.EXPORT_FAILED)
            }
        }
    }

    private fun File.toShareUri(): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

    fun onShareHandled() {
        _uiState.value = _uiState.value.copy(fileToShare = null)
    }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReadingFile = true, error = null)
            _uiState.value = try {
                val file = importer.stage(uri)
                stagedImport = file
                val preview = importer.preview(file)
                _uiState.value.copy(
                    isReadingFile = false,
                    preview = preview,
                    importSelection = preview.availableSections,
                    importCarIds = preview.cars.map { it.carId }.toSet(),
                    importMode = ImportMode.MERGE
                )
            } catch (e: NewerBackupException) {
                _uiState.value.copy(isReadingFile = false, error = BackupError.BACKUP_TOO_NEW)
            } catch (e: Exception) {
                _uiState.value.copy(isReadingFile = false, error = BackupError.NOT_A_BACKUP)
            }
        }
    }

    fun toggleImportSection(section: BackupSection) {
        val current = _uiState.value.importSelection
        _uiState.value = _uiState.value.copy(
            importSelection = if (section in current) current - section else current + section
        )
    }

    fun toggleImportCar(carId: Int) {
        val current = _uiState.value.importCarIds
        _uiState.value = _uiState.value.copy(
            importCarIds = if (carId in current) current - carId else current + carId
        )
    }

    fun setImportMode(mode: ImportMode) {
        _uiState.value = _uiState.value.copy(importMode = mode)
    }

    fun restore() {
        val file = stagedImport ?: return
        val state = _uiState.value
        if (state.isRestoring || state.importSelection.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = state.copy(isRestoring = true, error = null)
            _uiState.value = try {
                val report = importer.restore(
                    file = file,
                    selection = state.importSelection,
                    carIds = state.importCarIds,
                    mode = state.importMode
                )
                _uiState.value.copy(isRestoring = false, preview = null, report = report)
            } catch (e: Exception) {
                _uiState.value.copy(
                    isRestoring = false,
                    preview = null,
                    error = BackupError.RESTORE_FAILED
                )
            }
            stagedImport = null
        }
    }

    fun dismissPreview() {
        stagedImport = null
        _uiState.value = _uiState.value.copy(preview = null)
    }

    /** A restore can have added cars as well as rows, so both lists are stale afterwards. */
    fun dismissReport() {
        _uiState.value = _uiState.value.copy(report = null)
        viewModelScope.launch {
            val cars = exporter.cars()
            val carIds = _uiState.value.exportCarIds + cars.map { it.carId }
            _uiState.value = _uiState.value.copy(
                cars = cars,
                exportCarIds = carIds,
                counts = exporter.counts(carIds)
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
