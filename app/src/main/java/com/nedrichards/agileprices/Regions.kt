package com.nedrichards.agileprices

data class ElectricityRegion(
    val code: String,
    val name: String,
)

val ukElectricityRegions: List<ElectricityRegion> = listOf(
    ElectricityRegion("_A", "Eastern England"),
    ElectricityRegion("_B", "East Midlands"),
    ElectricityRegion("_C", "London"),
    ElectricityRegion("_D", "Merseyside and Northern Wales"),
    ElectricityRegion("_E", "West Midlands"),
    ElectricityRegion("_F", "North Eastern England"),
    ElectricityRegion("_G", "North Western England"),
    ElectricityRegion("_H", "Southern England"),
    ElectricityRegion("_J", "South Eastern England"),
    ElectricityRegion("_K", "Southern Wales"),
    ElectricityRegion("_L", "South Western England"),
    ElectricityRegion("_M", "Yorkshire"),
    ElectricityRegion("_N", "Southern Scotland"),
    ElectricityRegion("_P", "Northern Scotland"),
)

val regionCodeToName: Map<String, String> = ukElectricityRegions.associate { it.code to it.name }

