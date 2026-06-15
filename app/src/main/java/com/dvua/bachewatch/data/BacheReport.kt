package com.dvua.bachewatch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

//Modelo de datos para reportar un bachecito

@Entity(tableName = "bache_reports")
data class BacheReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,                //Título corto del reporte
    val description: String,          //Descripción escrita por el ciudadano
    val latitude: Double,             //Latitud del bache
    val longitude: Double,            //Longitud del bache
    val severity: String,             //"Leve", "Moderado" o "Crítico"
    val status: String,               //"Pendiente", "En Proceso" o "Reparado"
    val imageUrl: String?,            //Puede guardar una URI local, URL o imagen en Base 64
    val createdAt: Long = System.currentTimeMillis(),
    val upvotes: Int = 0,             //Likes de ciudadanos del reporte
    val referenceZone: String,        //Zona aproximada donde se registró el bache
    val reporterId: String = "anonymous",
    val reporterName: String = "Ciudadano anónimo"
)
