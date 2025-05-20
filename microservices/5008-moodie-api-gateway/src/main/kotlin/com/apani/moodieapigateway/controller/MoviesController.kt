package com.apani.moodieapigateway.controller

import com.apani.moodieapigateway.model.rest.AutoCompleteResponse
import com.apani.moodieapigateway.model.rest.MoviesResponse
import com.apani.moodieapigateway.model.rest.RecommendResponse
import com.apani.moodieapigateway.service.MovieService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/movies")
class MoviesController (
    private val movieService: MovieService
) {

    @GetMapping("/autocomplete")
    fun getAutoComplete(
        @RequestParam(required = false, defaultValue = "") query: String,
        @RequestParam(required = false, defaultValue = "5") limit: Int
    ): MoviesResponse {
        val suggestions = movieService.getAutoCompleteSuggestions(query, limit)
        return MoviesResponse(
            success = true,
            message = "Autocomplete suggestions retrieved successfully",
            data = AutoCompleteResponse(suggestions = suggestions)
        )
    }

    @GetMapping("/recommend")
    fun getRecommend(
        @RequestParam(required = true) movieId: String
    ): MoviesResponse {
//        val recommendations = movieService.getRecommendations(movieId)
        movieService.downloadLatestModel()

        return MoviesResponse(
            success = true,
            message = "Recommendations retrieved successfully",
        )
    }
}
