package com.example.newsapp.core.data

import com.example.newsapp.core.data.local.ArticlesDao
import com.example.newsapp.core.data.remote.NewsListDto
import com.example.newsapp.core.data.remote.toArticle
import com.example.newsapp.core.data.remote.toArticleEntity
import com.example.newsapp.core.data.remote.toNewsList
import com.example.newsapp.core.domain.Article
import com.example.newsapp.core.domain.NewsList
import com.example.newsapp.core.domain.NewsRepository
import com.example.newsapp.core.domain.NewsResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class NewsRepositoryImpl(
    private val httpClient: HttpClient,
    private val dao: ArticlesDao
): NewsRepository {

    private val tag = "NewsRepository: "

    private val baseUrl = "https://newsdata.io/api/1/latest"
    private val apiKey = "pub_a0073db11c5643a4a34aab78f991fee4"

    private suspend fun getLocalNews(nextPage: String?): NewsList {
        val localNews = dao.getArticleList()
        println(tag + "getLocalNews "+localNews.size + " nextPage: " + nextPage)

        val newsList = NewsList(
            nextPage = nextPage,
            articles = localNews.map { it.toArticle() },
        )
        return newsList
    }

    private suspend fun getRemoteNews(nextPage: String?): NewsList {
        val newsListDto: NewsListDto = httpClient.get(baseUrl) { // Also, ensure newsListDto is of type NewsListDto
            parameter("apiKey", apiKey)
            parameter("language", "en")
            if(nextPage != null) parameter("page", nextPage)
        }.body()

        println(tag + "getRemoteNews: "+ newsListDto.results?.size + " nextPage: " + nextPage)
        return newsListDto.toNewsList()
    }

    override suspend fun getNews(): Flow<NewsResult<NewsList>> {
        return flow {
            val remoteNewsList = try {
                getRemoteNews(null)
            }catch (e: Exception){
                e.printStackTrace()
                println(tag + "getNews remote exception: "+e.message)
                null
            }

            //get the data from remote source
            remoteNewsList?.let {
                dao.clearDatabase()
                dao.upsertArticleList(remoteNewsList.articles.map { it.toArticleEntity() })
                emit(NewsResult.Success(getLocalNews(remoteNewsList.nextPage)))
                return@flow
            }

            //if no remote source then use the local source
            val localNewsList = getLocalNews(null)
            if(localNewsList.articles.isNotEmpty()){
                emit(NewsResult.Success(localNewsList))
                return@flow
            }

            emit(NewsResult.Error("No data for news"))
        }
    }

    override suspend fun paginate(nextPage: String): Flow<NewsResult<NewsList>> {
        return flow {
            val remoteNewsList = try {
                getRemoteNews(nextPage)
            }catch (e: Exception){
                e.printStackTrace()
                println(tag + "paginate remote exception: "+e.message)
                null
            }

            //get the data from remote source
            remoteNewsList?.let {
                dao.upsertArticleList(remoteNewsList.articles.map { it.toArticleEntity() })


                //not getting them from the database like getNews()
                //because we will also get old items taht we already have before pagination
                emit(NewsResult.Success(remoteNewsList))
                return@flow
            }

            emit(NewsResult.Error("No data for news"))
        }
    }

    override suspend fun getArticle(articleId: String): Flow<NewsResult<Article>> {
        TODO("Not yet implemented")
    }


}