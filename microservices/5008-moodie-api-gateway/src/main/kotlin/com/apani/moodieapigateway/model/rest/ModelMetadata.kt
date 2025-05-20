package com.apani.moodieapigateway.model.rest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModelMetadata(
    @JsonProperty("version")
    val version: String,

    @JsonProperty("created_at")
    val created_at: String,

    @JsonProperty("title_embedding_shape")
    val title_embedding_shape: List<Int>,

    @JsonProperty("overview_embedding_shape")
    val overview_embedding_shape: List<Int>,

    @JsonProperty("combined_embedding_shape")
    val combined_embedding_shape: List<Int>,

    @JsonProperty("num_movies")
    val num_movies: Int,

    @JsonProperty("title_weight")
    val title_weight: Double,

    @JsonProperty("overview_weight")
    val overview_weight: Double,

    @JsonProperty("embedding_model")
    val embedding_model: String
)
