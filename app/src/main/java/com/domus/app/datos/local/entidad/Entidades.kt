package com.domus.app.datos.local.entidad

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.TipoMovimiento

/*
 * Las propiedades están en español, pero los nombres de tablas y columnas (`tableName` y
 * `@ColumnInfo(name = …)`) se mantienen tal como están en las bases de datos existentes:
 * cambiarlos rompería el esquema y obligaría a migrar los datos de los usuarios.
 */

@Entity(tableName = "profiles")
data class EntidadPerfil(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val nombre: String,
    @ColumnInfo(name = "avatar") val avatar: String,
    @ColumnInfo(name = "createdAt") val creadoEn: Long,
    @ColumnInfo(name = "updatedAt") val actualizadoEn: Long,
    @ColumnInfo(name = "isActive") val activo: Boolean,
    /** false: perfil de otro móvil del hogar (sin PIN; solo para mostrar nombres y trazabilidad). */
    @ColumnInfo(name = "isLocal", defaultValue = "1") val esLocal: Boolean = true,
)

@Entity(tableName = "households")
data class EntidadHogar(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val nombre: String,
    @ColumnInfo(name = "createdAt") val creadoEn: Long,
    @ColumnInfo(name = "updatedAt") val actualizadoEn: Long,
    @ColumnInfo(name = "isActive") val activo: Boolean,
    /** Identificador común del hogar en todos los móviles vinculados (null = solo este móvil). */
    @ColumnInfo(name = "syncId") val idSincronizacion: String? = null,
    /** Clave del hogar (32 bytes). Vive dentro de la base de datos cifrada con SQLCipher. */
    @ColumnInfo(name = "syncKey") val claveSincronizacion: ByteArray? = null,
    @ColumnInfo(name = "lastSyncAt") val ultimaSincronizacion: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntidadHogar) return false
        return id == other.id && nombre == other.nombre && creadoEn == other.creadoEn &&
            actualizadoEn == other.actualizadoEn && activo == other.activo &&
            idSincronizacion == other.idSincronizacion &&
            (claveSincronizacion?.contentEquals(other.claveSincronizacion) ?: (other.claveSincronizacion == null)) &&
            ultimaSincronizacion == other.ultimaSincronizacion
    }

    override fun hashCode(): Int {
        var resultado = id.hashCode()
        resultado = 31 * resultado + nombre.hashCode()
        resultado = 31 * resultado + (idSincronizacion?.hashCode() ?: 0)
        resultado = 31 * resultado + (claveSincronizacion?.contentHashCode() ?: 0)
        return resultado
    }
}

@Entity(
    tableName = "household_members",
    primaryKeys = ["householdId", "profileId"],
    foreignKeys = [
        ForeignKey(
            entity = EntidadHogar::class,
            parentColumns = ["id"],
            childColumns = ["householdId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = EntidadPerfil::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("profileId")],
)
data class EntidadMiembroHogar(
    @ColumnInfo(name = "householdId") val idHogar: Long,
    @ColumnInfo(name = "profileId") val idPerfil: String,
    @ColumnInfo(name = "joinedAt") val unidoEn: Long,
    @ColumnInfo(name = "isActive") val activo: Boolean,
    /** Última modificación de la pertenencia (resolución de conflictos al sincronizar). */
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val actualizadoEn: Long = unidoEn,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = EntidadPerfil::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = EntidadHogar::class,
            parentColumns = ["id"],
            childColumns = ["householdId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("profileId"), Index("householdId"), Index(value = ["scope", "type"])],
)
data class EntidadCategoria(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val nombre: String,
    @ColumnInfo(name = "type") val tipo: TipoMovimiento,
    @ColumnInfo(name = "scope") val ambito: AmbitoCategoria,
    @ColumnInfo(name = "profileId") val idPerfil: String?,
    @ColumnInfo(name = "householdId") val idHogar: Long?,
    @ColumnInfo(name = "icon") val icono: String?,
    @ColumnInfo(name = "isActive") val activa: Boolean,
    @ColumnInfo(name = "createdAt") val creadaEn: Long,
    /** Última modificación (resolución de conflictos al sincronizar). */
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val actualizadaEn: Long = creadaEn,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = EntidadCategoria::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = EntidadPerfil::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = EntidadHogar::class,
            parentColumns = ["id"],
            childColumns = ["householdId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = EntidadPerfil::class,
            parentColumns = ["id"],
            childColumns = ["createdByProfileId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index("profileId"),
        Index("householdId"),
        Index("createdByProfileId"),
        Index(value = ["scope", "transactionDate"]),
    ],
)
data class EntidadMovimiento(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "scope") val ambito: AmbitoMovimiento,
    @ColumnInfo(name = "type") val tipo: TipoMovimiento,
    @ColumnInfo(name = "amountMinor") val importeCentimos: Long,
    @ColumnInfo(name = "currencyCode") val codigoMoneda: String,
    @ColumnInfo(name = "description") val descripcion: String?,
    @ColumnInfo(name = "categoryId") val idCategoria: String,
    @ColumnInfo(name = "profileId") val idPerfil: String?,
    @ColumnInfo(name = "householdId") val idHogar: Long?,
    @ColumnInfo(name = "createdByProfileId") val creadoPor: String,
    @ColumnInfo(name = "updatedByProfileId") val actualizadoPor: String?,
    @ColumnInfo(name = "deletedByProfileId") val eliminadoPor: String?,
    /** Fecha del movimiento como día epoch (LocalDate.toEpochDay). */
    @ColumnInfo(name = "transactionDate") val fecha: Long,
    @ColumnInfo(name = "createdAt") val creadoEn: Long,
    @ColumnInfo(name = "updatedAt") val actualizadoEn: Long,
    @ColumnInfo(name = "deletedAt") val eliminadoEn: Long?,
)

@Entity(
    tableName = "security_credentials",
    foreignKeys = [
        ForeignKey(
            entity = EntidadPerfil::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EntidadCredencialSeguridad(
    @PrimaryKey @ColumnInfo(name = "profileId") val idPerfil: String,
    @ColumnInfo(name = "salt") val sal: ByteArray,
    @ColumnInfo(name = "verifier") val verificador: ByteArray,
    @ColumnInfo(name = "algorithm") val algoritmo: String,
    @ColumnInfo(name = "failedAttempts") val intentosFallidos: Int,
    @ColumnInfo(name = "lockoutUntil") val bloqueadoHasta: Long?,
    @ColumnInfo(name = "recoveryVerifier") val verificadorRecuperacion: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntidadCredencialSeguridad) return false
        return idPerfil == other.idPerfil &&
            sal.contentEquals(other.sal) &&
            verificador.contentEquals(other.verificador) &&
            algoritmo == other.algoritmo &&
            intentosFallidos == other.intentosFallidos &&
            bloqueadoHasta == other.bloqueadoHasta &&
            verificadorRecuperacion == other.verificadorRecuperacion
    }

    override fun hashCode(): Int {
        var resultado = idPerfil.hashCode()
        resultado = 31 * resultado + sal.contentHashCode()
        resultado = 31 * resultado + verificador.contentHashCode()
        resultado = 31 * resultado + algoritmo.hashCode()
        resultado = 31 * resultado + intentosFallidos
        resultado = 31 * resultado + (bloqueadoHasta?.hashCode() ?: 0)
        resultado = 31 * resultado + (verificadorRecuperacion?.hashCode() ?: 0)
        return resultado
    }
}

/* ------------------------------ Proyecciones de consultas ------------------------------ */

data class FilaDetalleMovimiento(
    @Embedded val movimiento: EntidadMovimiento,
    @ColumnInfo(name = "categoryName") val nombreCategoria: String,
    @ColumnInfo(name = "categoryIcon") val iconoCategoria: String?,
    @ColumnInfo(name = "createdByName") val nombreCreador: String?,
    @ColumnInfo(name = "updatedByName") val nombreEditor: String?,
    @ColumnInfo(name = "deletedByName") val nombreEliminador: String?,
)

data class FilaTotalPorTipo(
    @ColumnInfo(name = "type") val tipo: TipoMovimiento,
    @ColumnInfo(name = "total") val total: Long,
)

data class FilaTotalCategoria(
    @ColumnInfo(name = "categoryId") val idCategoria: String,
    @ColumnInfo(name = "name") val nombre: String,
    @ColumnInfo(name = "icon") val icono: String?,
    @ColumnInfo(name = "total") val total: Long,
)

data class FilaMiembro(
    @Embedded val perfil: EntidadPerfil,
    @ColumnInfo(name = "joinedAt") val unidoEn: Long,
)

/** Pertenencia con los datos del perfil, incluidas las inactivas (para sincronizar). */
data class FilaMiembroSinc(
    @Embedded val perfil: EntidadPerfil,
    @ColumnInfo(name = "memberJoinedAt") val unidoEn: Long,
    @ColumnInfo(name = "memberIsActive") val miembroActivo: Boolean,
    @ColumnInfo(name = "memberUpdatedAt") val miembroActualizadoEn: Long,
)
