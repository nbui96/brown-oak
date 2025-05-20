package com.apani.moodieapigateway.service

import com.apani.moodieapigateway.model.db.Movie
import com.apani.moodieapigateway.model.rest.MovieAutoComplete
import com.apani.moodieapigateway.repo.MovieRepo
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.runBlocking
import movieservice.MovieRequest
import movieservice.MovieServiceGrpcKt
import movieservice.Recommendations
import org.springframework.stereotype.Service

@Service
class MovieService(
    private val movieRepo: MovieRepo,
    private val s3Service: S3Service
) {

    private val channel: ManagedChannel by lazy {
        ManagedChannelBuilder
            .forAddress("moodie-smart-core", 5009)
            .usePlaintext()
            .build()
    }

    private val grpcClient = MovieServiceGrpcKt.MovieServiceCoroutineStub(channel)

    fun getRandomMovies(limit: Int = 10): List<Movie> {
        return try {
            movieRepo.fetchRandomMovies(limit)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAutoCompleteSuggestions(
        query: String,
        limit: Int
    ): List<MovieAutoComplete> {
        if (query.isBlank()) return emptyList()

        return movieRepo.getAutoCompleteSuggestions(query, limit)
    }

    fun getRecommendations(
        movieId: String
    ): Recommendations = runBlocking {
        val grpcMovieRequest = MovieRequest.newBuilder()
            .setMovieId(movieId)
            .build()

        val recommendationsResponse = grpcClient.recommend(grpcMovieRequest)

        return@runBlocking recommendationsResponse
    }
}
