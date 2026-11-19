package co.edu.uniandes.vinilos.ui

import android.graphics.Rect
import android.view.View
import android.widget.RatingBar
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.FailureHandler
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.robots.AlbumListRobot
import co.edu.uniandes.vinilos.robots.CommentCreateRobot
import co.edu.uniandes.vinilos.robots.TrackCreateRobot
import co.edu.uniandes.vinilos.testing.BackendCollectors
import co.edu.uniandes.vinilos.testing.BackendFirewall
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.testing.waitUntilGone
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.isA
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Hoja "Comentar álbum" y comentarios del detalle contra BackVynils local; un método por caso E2E del inventario de
 * docs/06. Cada caso crea su álbum y sus coleccionistas con nombres únicos y no depende de la semilla. La regla borra
 * al final los comentarios y los coleccionistas de la prueba.
 */
@RunWith(AndroidJUnit4::class)
class CommentCreateScreenTest {
    private val data = BackendCollectors()
    private val firewall = BackendFirewall()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule val ruleChain: TestRule = RuleChain.outerRule(data).around(firewall).around(activityRule)

    private val catalog = AlbumListRobot()
    private val sheet = CommentCreateRobot()
    private val trackSheet = TrackCreateRobot()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun publishes_a_comment_that_appears_last_with_its_author_and_is_saved_once() {
        val author = collectorName("Ana", "Pardo")
        val collectorId = data.createCollector(author, email())
        val album = openOwnAlbum()
        openSheet(album.name)

        sheet
            .chooseCollector(author)
            .chooseRating(4)
            .assertRating(4)
            .typeComment("Una obra que no envejece.")
            .tapPublish()

        awaitNoticeOrComment("Una obra que no envejece.")
        waitUntilGone(withId(R.id.sheetTitle))
        assertComment(author, initials = "AP", rating = 4, text = "Una obra que no envejece.")
        assertLastComment(author, rating = 4, text = "Una obra que no envejece.")
        onView(withText(R.string.album_detail_no_comments)).check(doesNotExist())
        assertSavedOnce(album.id, collectorId, rating = 4, text = "Una obra que no envejece.")
    }

    @Test
    fun required_errors_clear_per_field_and_blank_text_is_still_required() {
        val author = collectorName("Bruno", "Rey")
        val collectorId = data.createCollector(author, email())
        val album = openOwnAlbum()
        openSheet(album.name)

        sheet
            .assertErrors(collector = false, rating = false, description = false)
            .tapPublish()
            .assertErrors(collector = true, rating = true, description = true)
            .assertOpen(album.name)
            .chooseCollector(author)
            .assertErrors(collector = false, rating = true, description = true)
            .chooseRating(2)
            .assertErrors(collector = false, rating = false, description = true)
            .typeComment("   ")
            .tapPublish()
            .assertErrors(collector = false, rating = false, description = true)
            .assertOpen(album.name)
            .typeComment("Ahora sí tiene texto.")
            .assertErrors(collector = false, rating = false, description = false)
            .tapCancel()

        assertClosedWithoutComments(album.id, collectorId)
    }

    @Test
    fun cancel_and_close_write_nothing_and_reopen_an_empty_form() {
        val author = collectorName("Carla", "Soto")
        val collectorId = data.createCollector(author, email())
        val album = openOwnAlbum()

        openSheet(album.name)
        sheet
            .chooseCollector(author)
            .chooseRating(5)
            .typeComment("Comentario descartado.")
            .tapCancel()
        assertClosedWithoutComments(album.id, collectorId)

        openSheet(album.name)
        sheet
            .assertEmptyForm(author)
            .chooseCollector(author)
            .chooseRating(1)
            .typeComment("Comentario descartado.")
            .tapClose()
        assertClosedWithoutComments(album.id, collectorId)
        onView(withText("Comentario descartado.")).check(doesNotExist())
    }

    @Test
    fun network_failure_keeps_the_form_and_a_retry_publishes_once() {
        val author = collectorName("Diego", "Luna")
        val collectorId = data.createCollector(author, email())
        val album = openOwnAlbum()
        openSheet(album.name)
        sheet.chooseCollector(author).chooseRating(3).typeComment("Se publica al volver la red.")

        try {
            firewall.block()
            sheet
                .tapPublish()
                .assertNetworkError()
                .assertOpen(album.name)
                .assertCollectorChosen(author)
                .assertRating(3)
                .assertComment("Se publica al volver la red.")
        } finally {
            firewall.restore()
        }

        sheet.tapPublish()
        awaitPublished("Se publica al volver la red.")
        assertComment(author, initials = "DL", rating = 3, text = "Se publica al volver la red.")
        assertSavedOnce(album.id, collectorId, rating = 3, text = "Se publica al volver la red.")
    }

    @Test
    fun visitor_sees_the_comments_heading_without_the_comment_button() {
        val album = openOwnAlbum(roleRes = R.string.role_visitor)

        revealDetailView(R.id.sectionTitle)
        onView(allOf(withId(R.id.sectionTitle), withText(R.string.album_detail_comments)))
            .check(matches(isDisplayed()))
        onView(withId(R.id.sectionAction)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withText(R.string.album_detail_no_comments)).check(matches(isDisplayed()))
        assertTrue(data.commentsOn(album.id).isEmpty())
    }

    @Test
    fun recreation_keeps_the_chosen_collector_rating_and_text() {
        val author = collectorName("Elena", "Mora")
        val collectorId = data.createCollector(author, email())
        val album = openOwnAlbum()
        openSheet(album.name)
        sheet.chooseCollector(author).chooseRating(2).typeComment("Sobrevive a la recreación.")

        activityRule.scenario.recreate()

        sheet
            .assertOpen(album.name)
            .assertCollectorChosen(author)
            .assertRating(2)
            .assertComment("Sobrevive a la recreación.")
            .assertErrors(collector = false, rating = false, description = false)
            .tapPublish()
        awaitPublished("Sobrevive a la recreación.")
        assertComment(author, initials = "EM", rating = 2, text = "Sobrevive a la recreación.")
        assertSavedOnce(album.id, collectorId, rating = 2, text = "Sobrevive a la recreación.")
    }

    @Test
    fun comments_and_a_track_added_in_both_orders_show_on_the_same_album() {
        val author = collectorName("Fabio", "Gil")
        data.createCollector(author, email())
        val album = openOwnAlbum()

        openSheet(album.name)
        sheet
            .chooseCollector(author)
            .chooseRating(5)
            .typeComment("Primero el comentario.")
            .tapPublish()
        awaitPublished("Primero el comentario.")
        assertComment(author, initials = "FG", rating = 5, text = "Primero el comentario.")

        waitForDisplayed(withId(R.id.primaryAction)).perform(click())
        trackSheet
            .assertOpen()
            .assertSubtitle(album.name, number = 1)
            .typeName("Después la canción")
            .enterDigits(3, 0, 5)
            .assertDuration("03:05")
            .tapSave()
        waitUntilGone(withId(R.id.sheetTitle))
        awaitTrackCount("1")
        // The track notice sits over the end of the list and can cover "Comentar", as in HU11.
        waitUntilGone(withText(R.string.snack_track_added))

        openSheet(album.name)
        sheet
            .chooseCollector(author)
            .chooseRating(3)
            .typeComment("Y otro comentario al final.")
            .tapPublish()
        awaitPublished("Y otro comentario al final.")

        assertComment(author, initials = "FG", rating = 3, text = "Y otro comentario al final.")
        assertLastComment(author, rating = 3, text = "Y otro comentario al final.")
        assertComment(author, initials = "FG", rating = 5, text = "Primero el comentario.")
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText("Después la canción"))),
        )
        onView(allOf(withId(R.id.trackNumber), withText("01"))).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.trackDuration), withText("3:05"), isDisplayed())).check(matches(isDisplayed()))
        val saved = data.commentsOn(album.id).map { it.description }
        assertEquals(listOf("Primero el comentario.", "Y otro comentario al final."), saved.sorted())
    }

    @Test
    fun comment_authors_keep_their_names_after_reopening_the_detail() {
        val earlier = collectorName("Gloria", "Paz")
        val earlierId = data.createCollector(earlier, email())
        val author = collectorName("Hugo", "Vela")
        data.createCollector(author, email())
        val albumName = albumName()
        val albumId = data.createAlbum(albumName)
        data.addComment(albumId, earlierId, "Escrito fuera de la app.", rating = 5)
        catalog.enterAs(R.string.role_collector)
        openAlbum(albumName)
        assertComment(earlier, initials = "GP", rating = 5, text = "Escrito fuera de la app.")

        openSheet(albumName)
        sheet
            .chooseCollector(author)
            .chooseRating(4)
            .typeComment("Publicado desde la app.")
            .tapPublish()
        awaitPublished("Publicado desde la app.")
        assertComment(author, initials = "HV", rating = 4, text = "Publicado desde la app.")

        leaveDetail()
        openAlbum(albumName)

        assertComment(earlier, initials = "GP", rating = 5, text = "Escrito fuera de la app.")
        assertComment(author, initials = "HV", rating = 4, text = "Publicado desde la app.")
        assertLastComment(author, rating = 4, text = "Publicado desde la app.")
        onView(allOf(withId(R.id.commentAuthor), withText(R.string.album_detail_comment_author)))
            .check(doesNotExist())
    }

    private data class OwnAlbum(
        val id: Int,
        val name: String,
    )

    private fun openOwnAlbum(roleRes: Int = R.string.role_collector): OwnAlbum {
        val name = albumName()
        val id = data.createAlbum(name)
        catalog.enterAs(roleRes)
        openAlbum(name)
        return OwnAlbum(id, name)
    }

    private fun openAlbum(name: String) {
        catalog.scrollTo(name)
        onView(withId(R.id.albumList)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText(name)), click()),
        )
        revealDetailView(R.id.albumName)
        onView(allOf(withId(R.id.albumName), withText(name))).check(matches(isDisplayed()))
    }

    /** Una fila de cabecera puede superar el alto visible; se muestra el hijo que se va a comprobar. */
    private fun revealDetailView(viewId: Int) {
        waitForDisplayed(withId(R.id.detailList)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withId(viewId)),
                object : ViewAction {
                    override fun getConstraints() = isA(View::class.java)

                    override fun getDescription() = "desplazar el detalle hasta la vista $viewId"

                    override fun perform(
                        uiController: UiController,
                        view: View,
                    ) {
                        val target = view.findViewById<View>(viewId)
                        target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true)
                        uiController.loopMainThreadUntilIdle()
                    }
                },
            ),
        )
    }

    private fun openSheet(albumName: String) {
        revealDetailView(R.id.sectionAction)
        onView(allOf(withId(R.id.sectionAction), withText(R.string.album_action_comment))).perform(click())
        sheet.assertOpen(albumName)
    }

    /**
     * El aviso dura poco y en API 21 puede cerrarse antes de que Espresso lo mire. Sirve el aviso o la tarjeta,
     * que ya está en el detalle cuando el aviso se fue.
     */
    private fun awaitNoticeOrComment(text: String) {
        val deadline = System.currentTimeMillis() + AWAIT_MILLIS
        val quiet = FailureHandler { error, _ -> throw error }
        val notice = withText(R.string.snack_comment_published)
        val comment = allOf(withId(R.id.commentBody), withText(text))
        while (System.currentTimeMillis() < deadline) {
            if (isShown(notice, quiet) || isShown(comment, quiet)) return
            Thread.sleep(POLL_MILLIS)
        }
        onView(comment).check(matches(isDisplayed()))
    }

    private fun isShown(
        matcher: Matcher<View>,
        quiet: FailureHandler,
    ): Boolean =
        try {
            onView(matcher).withFailureHandler(quiet).check(matches(isDisplayed()))
            true
        } catch (_: NoMatchingViewException) {
            false
        } catch (_: AssertionError) {
            false
        }

    /**
     * Espera la tarjeta del comentario publicado, que va al final de la lista y no desaparece. El aviso "Comentario
     * publicado" dura poco y sale mientras la hoja libera el foco: solo CP-HU09-01 lo comprueba.
     */
    private fun awaitPublished(text: String) {
        waitUntilGone(withId(R.id.sheetTitle))
        waitForDisplayed(
            allOf(withId(R.id.commentBody), withText(text), hasWindowFocus()),
            prepare = {
                onView(withId(R.id.detailList))
                    .perform(RecyclerViewActions.scrollToLastPosition<RecyclerView.ViewHolder>())
            },
        )
    }

    private fun awaitTrackCount(count: String) {
        revealDetailView(R.id.tracksInfo)
        waitForDisplayed(
            allOf(
                withId(R.id.infoValue),
                isDescendantOfA(withId(R.id.tracksInfo)),
                withText(count),
                hasWindowFocus(),
            ),
        )
    }

    /**
     * La tarjeta se lee como un solo elemento con autor, estrellas y texto. El autor puede llegar después del
     * primer dibujo (índice de autores), así que se espera la descripción completa.
     */
    private fun assertComment(
        author: String,
        initials: String,
        rating: Int,
        text: String,
    ) {
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(text))),
        )
        val card = withContentDescription(cardDescription(author, rating, text))
        awaitWithoutCapture(allOf(card, hasWindowFocus()))
        onView(allOf(withId(R.id.commentAuthor), isDescendantOfA(card))).check(matches(withText(author)))
        onView(allOf(withId(R.id.commentAvatar), isDescendantOfA(card))).check(matches(withText(initials)))
        onView(allOf(withId(R.id.commentRating), isDescendantOfA(card))).check(matches(hasRating(rating)))
        onView(allOf(withId(R.id.commentBody), isDescendantOfA(card))).check(matches(withText(text)))
    }

    // The author can arrive after the first frame, so this check may fail several times before it passes. Espresso's
    // failure handler takes a screenshot on each failure and, on API 21, a run of them freezes the emulator (Sprint 2,
    // diferencias entre API 21 y 37, I2): the poll rethrows instead and only the last failure is reported.
    private fun awaitWithoutCapture(matcher: Matcher<View>) {
        val deadline = System.currentTimeMillis() + AWAIT_MILLIS
        while (true) {
            val failure =
                try {
                    onView(matcher).withFailureHandler { error, _ -> throw error }.check(matches(isDisplayed()))
                    null
                } catch (error: NoMatchingViewException) {
                    error
                } catch (error: AssertionError) {
                    error
                }
            if (failure == null) return
            if (System.currentTimeMillis() > deadline) throw failure
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun assertLastComment(
        author: String,
        rating: Int,
        text: String,
    ) {
        onView(withId(R.id.detailList))
            .perform(RecyclerViewActions.scrollToLastPosition<RecyclerView.ViewHolder>())
            .check(matches(lastRowDescribedAs(cardDescription(author, rating, text))))
    }

    private fun assertSavedOnce(
        albumId: Int,
        collectorId: Int,
        rating: Int,
        text: String,
    ) {
        val saved = data.commentsOn(albumId).single()
        assertEquals(text, saved.description)
        assertEquals(rating, saved.rating)
        assertTrue(
            "El comentario ${saved.id} no es del coleccionista $collectorId",
            saved.id in data.commentIdsOf(collectorId),
        )
    }

    private fun assertClosedWithoutComments(
        albumId: Int,
        collectorId: Int,
    ) {
        waitUntilGone(withId(R.id.sheetTitle))
        onView(withText(R.string.snack_comment_published)).check(doesNotExist())
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(withText(R.string.album_detail_no_comments)),
        )
        onView(withText(R.string.album_detail_no_comments)).check(matches(isDisplayed()))
        onView(withId(R.id.commentAuthor)).check(doesNotExist())
        assertTrue(data.commentsOn(albumId).isEmpty())
        assertTrue(data.commentIdsOf(collectorId).isEmpty())
    }

    private fun leaveDetail() {
        onView(withId(R.id.headerBack)).perform(click())
        waitForDisplayed(withId(R.id.albumList))
    }

    private fun cardDescription(
        author: String,
        rating: Int,
        text: String,
    ): String = context.getString(R.string.album_detail_comment_description, author, rating, text)

    private fun hasRating(rating: Int): Matcher<View> =
        object : BoundedMatcher<View, RatingBar>(RatingBar::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("rating $rating")
            }

            override fun matchesSafely(bar: RatingBar): Boolean = bar.rating == rating.toFloat()
        }

    /** El comentario recién publicado va al final de la lista del detalle. */
    private fun lastRowDescribedAs(expected: String): Matcher<View> =
        object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("last row described as \"$expected\"")
            }

            override fun matchesSafely(list: RecyclerView): Boolean {
                val last = (list.adapter?.itemCount ?: 0) - 1
                val row = list.findViewHolderForAdapterPosition(last)?.itemView ?: return false
                return row.contentDescription?.toString() == expected
            }
        }

    /** La pantalla anterior puede seguir en la jerarquía sin foco. Espresso sí la cuenta. */
    private fun hasWindowFocus(): Matcher<View> =
        object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("has window focus")
            }

            override fun matchesSafely(view: View): Boolean = view.hasWindowFocus()
        }

    private fun collectorName(
        first: String,
        last: String,
    ) = "$first ${token()} $last"

    private fun albumName() = "Comentarios ${token()}"

    private fun email() = "c${token()}@example.com"

    private fun token() = System.nanoTime().toString()
}

private const val POLL_MILLIS = 200L
private const val AWAIT_MILLIS = 15_000L
