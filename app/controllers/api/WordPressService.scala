package controllers.api

import cloud.{CachedWSCall, PagedContent}
import play.api.libs.ws.WS
import play.api.mvc.{Action, Call, Controller}
import models.wordpress.{WPPosts, WPPost, WPCategory, WPTag, WPAuthor}
import play.api.libs.json.{Json, JsValue, Writes}


/**
 * Adapters for Wordpress Webservices API
 */
object WordPressService extends Controller {

  implicit val timeout: Long = 15000

  /**
   * @return authors from Xebia blogs
   */
  def authors = Action {
    CachedWSCall("http://blog.xebia.fr/wp-json-api/get_author_index/").mapJson {
      jsonFetched => (jsonFetched \ "authors").as[Seq[JsValue]] map (_.as[WPAuthor])
    }.fold(
      errorMessage => {
        InternalServerError(errorMessage)
      },
      response => {
        Ok(Json.toJson(response))
      }
    )
  }

  /**
   * @return tags from Xebia blogs
   */
  def tags = Action {
    CachedWSCall("http://blog.xebia.fr/wp-json-api/get_tag_index/").mapJson {
      jsonFetched => (jsonFetched \ "tags").as[Seq[JsValue]] map (_.as[WPTag])
    }.fold(
      errorMessage => {
        InternalServerError(errorMessage)
      },
      response => {
        Ok(Json.toJson(response))
      }
    )
  }


  /**
   * @return categories of posts from Xebia blogs
   */
  def categories = Action {
    CachedWSCall("http://blog.xebia.fr/wp-json-api/get_category_index/").mapJson {
      jsonFetched => (jsonFetched \ "categories").as[Seq[JsValue]] map (_.as[WPCategory])
    }.fold(
      errorMessage => {
        InternalServerError(errorMessage)
      },
      response => {
        Ok(Json.toJson(response))
      }
    )
  }

  /**
   * @param id id of the post
   * @return tags of the post
   */
  def tagPosts(id: Long, count: Option[Int] = None, page: Option[Int]) = {
    posts("tag", Some(id), count, page, WPPosts.WPPostsFormat) {
      page => routes.WordPressService.tagPosts(id, count, Some(page))
    }
  }

  /**
   * @param id id of the post
   * @return tags of the post
   */
  def tagPostsIds(id: Long, count: Option[Int] = None, page: Option[Int]) = {
    posts("tag", Some(id), count, page, WPPosts.WPPostsIdsWrites) {
      page => routes.WordPressService.tagPostsIds(id, count, Some(page))
    }
  }

  /**
   * @param id id of the post
   * @return category of the post
   */
  def categoryPosts(id: Long, count: Option[Int] = None, page: Option[Int]) = {
    posts("category", Some(id), count, page, WPPosts.WPPostsFormat) {
      page => routes.WordPressService.categoryPosts(id, count, Some(page))
    }
  }

  /**
   * @param id id of the post
   * @return category of the post
   */
  def categoryPostsIds(id: Long, count: Option[Int] = None, page: Option[Int]) = {
    posts("category", Some(id), count, page, WPPosts.WPPostsIdsWrites) {
      page => routes.WordPressService.categoryPostsIds(id, count, Some(page))
    }
  }

  /**
   * @param id id of the post
   * @return author of the post
   */
  def authorPosts(id: Long, count: Option[Int] = None, page: Option[Int]) = {
    posts("author", Some(id), count, page, WPPosts.WPPostsFormat) {
      page => routes.WordPressService.authorPosts(id, count, Some(page))
    }
  }

  /**
   * @param id id of the post
   * @return author of the post
   */
  def authorPostsIds(id: Long, count: Option[Int] = None, page: Option[Int]) = {
    posts("author", Some(id), count, page, WPPosts.WPPostsIdsWrites) {
      page => routes.WordPressService.authorPostsIds(id, count, Some(page))
    }
  }

  /**
   * @return recent posts
   */
  def recentPosts(count: Option[Int] = None, page: Option[Int]) = {
    posts("recent", None, count, page, WPPosts.WPPostsFormat) {
      page => routes.WordPressService.recentPosts(count, Some(page))
    }

  }

  /**
   * @return recent posts ids
   */
  def recentPostsIds(count: Option[Int] = None, page: Option[Int]) = {
    posts("recent", None, count, page, WPPosts.WPPostsIdsWrites) {
      page => routes.WordPressService.recentPostsIds(count, Some(page))
    }
  }


  /**
   * @param _type type of entity fetched
   * @param id optional id of a post or all posts if None
   * @param count number of elements fetched
   * @param page # of the page for pagination
   * @param postWriter how to transform the wp post to JSON format for output
   * @param urlToPage function that generate the URL of the requested page with the page number as a function
   * @return _type element from a post identified by id or all posts limited by count
   */
  def posts(_type: String, id: Option[Long] = None, count: Option[Int] = None, page: Option[Int], postWriter: Writes[WPPosts])(urlToPage: (Int => Call)) = Action {
    implicit Request => {
      val wpPostsUrl = "http://blog.xebia.fr/wp-json-api/get_%1$s_posts/".format(_type)
      var queryStringParams = count.map(x => Seq("count" -> x.toString)).getOrElse(Seq())

      if (id.isDefined) {
        queryStringParams = queryStringParams.:+("id" -> id.get.toString)
      }

      val currentPage: Int = page.getOrElse(1)
      queryStringParams = queryStringParams.:+("page" -> currentPage.toString)

      val wpPostsRequestHolder = WS.url(wpPostsUrl)
        .withQueryString(queryStringParams.toArray: _*)

      val responsePost = CachedWSCall(wpPostsRequestHolder, 60).mapJson {
        jsonFetched => (jsonFetched).as[WPPosts]
      }

      responsePost.fold(
        message => InternalServerError(message),
        response => {
          val links = PagedContent(response.count, response.pages, currentPage)(urlToPage).getHeader
          Ok(Json.toJson(response)(postWriter)).withHeaders(links)

        }
      )
    }
  }

  /**
   *
   * @param id id of the post
   * @return the post identified by its id
   */
  def showPost(id: Long) = Action {
    val url: String = "http://blog.xebia.fr/wp-json-api/get_post"

    val ws = WS
      .url(url)
      .withQueryString(("post_id" -> id.toString))

    CachedWSCall(ws).mapJson {
      jsonFetched => (jsonFetched \ "post").as[WPPost]
    }.fold(
      errorMessage => {
        InternalServerError(errorMessage)
      },
      response => {
        Ok(Json.toJson(response)(WPPost.WPPostFormat))
      }
    )
  }

}
