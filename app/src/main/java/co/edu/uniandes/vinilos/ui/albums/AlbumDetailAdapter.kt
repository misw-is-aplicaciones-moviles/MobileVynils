package co.edu.uniandes.vinilos.ui.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailCommentBinding
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailHeaderBinding
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailMessageBinding
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailSectionBinding
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailTrackBinding
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.Track
import com.bumptech.glide.Glide
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One scrolling surface for the album header, section labels, tracks and comments. */
internal class AlbumDetailAdapter(
    private val onArtistClick: () -> Unit,
    private val onCommentClick: () -> Unit,
) : ListAdapter<AlbumDetailRow, RecyclerView.ViewHolder>(RowDiff) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    /** [canEdit] shows "Comentar" to the collector; [highlightedCommentId] is the comment just published here. */
    fun submitAlbum(
        album: Album,
        canEdit: Boolean,
        highlightedCommentId: Int?,
        commentInitials: Map<Int, String>,
    ) {
        submitList(
            buildList {
                add(album.toHeaderRow())
                add(AlbumDetailRow.Message(R.string.album_detail_tracks, isHeading = true))
                album.tracks.forEachIndexed { index, track -> add(AlbumDetailRow.Song(index + 1, track)) }
                if (album.tracks.isEmpty()) add(AlbumDetailRow.Message(R.string.album_detail_no_tracks))
                add(AlbumDetailRow.CommentsHeading(canEdit))
                album.comments.forEach { add(it.toRow(commentInitials[it.id], it.id == highlightedCommentId)) }
                if (album.comments.isEmpty()) add(AlbumDetailRow.Message(R.string.album_detail_no_comments))
            },
        )
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is AlbumDetailRow.Header -> HEADER
            is AlbumDetailRow.Song -> TRACK
            is AlbumDetailRow.Review -> COMMENT
            is AlbumDetailRow.Message -> MESSAGE
            is AlbumDetailRow.CommentsHeading -> SECTION
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            HEADER -> HeaderHolder(ItemAlbumDetailHeaderBinding.inflate(inflater, parent, false), onArtistClick)
            TRACK -> TrackHolder(ItemAlbumDetailTrackBinding.inflate(inflater, parent, false))
            COMMENT -> CommentHolder(ItemAlbumDetailCommentBinding.inflate(inflater, parent, false))
            MESSAGE -> MessageHolder(ItemAlbumDetailMessageBinding.inflate(inflater, parent, false))
            SECTION -> SectionHolder(ItemAlbumDetailSectionBinding.inflate(inflater, parent, false), onCommentClick)
            else -> error("Unknown album detail row")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val row = getItem(position)) {
            is AlbumDetailRow.Header -> (holder as HeaderHolder).bind(row)
            is AlbumDetailRow.Song -> (holder as TrackHolder).bind(row)
            is AlbumDetailRow.Review -> (holder as CommentHolder).bind(row)
            is AlbumDetailRow.Message -> (holder as MessageHolder).bind(row)
            is AlbumDetailRow.CommentsHeading -> (holder as SectionHolder).bind(row)
        }
    }

    private class HeaderHolder(
        private val binding: ItemAlbumDetailHeaderBinding,
        private val onArtistClick: () -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        // One per holder: a header bind no longer builds its own formatter.
        private val dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault())

        fun bind(header: AlbumDetailRow.Header) {
            val context = binding.root.context
            binding.albumName.text = header.name
            ViewCompat.setAccessibilityHeading(binding.albumName, true)
            binding.albumGenreYear.text =
                context.getString(R.string.album_genre_year, header.genreName, header.releaseDate.year)
            binding.albumArtist.text = header.performerName ?: context.getString(R.string.album_no_artist)
            binding.albumDescription.text = header.description
            bindArtistNavigation(header.artist)
            bindArtwork(header)
            bindInfo(header)
        }

        private fun bindArtistNavigation(artist: PerformerSummary?) {
            val canOpen = artist != null && artist.id > 0 && artist.type != null
            binding.artistLink.setOnClickListener(if (canOpen) View.OnClickListener { onArtistClick() } else null)
            binding.artistLink.isClickable = canOpen
            binding.artistLink.isFocusable = canOpen
            binding.artistLink.contentDescription =
                if (canOpen) binding.root.context.getString(R.string.artist_open_detail, artist?.name) else null
            binding.artistLink.importantForAccessibility =
                if (canOpen) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
            binding.albumArtist.importantForAccessibility =
                if (canOpen) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }

        private fun bindArtwork(header: AlbumDetailRow.Header) {
            Glide
                .with(binding.albumArtwork.coverView)
                .load(header.coverUrl)
                .placeholder(R.drawable.ic_nav_albums)
                .error(R.drawable.ic_nav_albums)
                .centerCrop()
                .into(binding.albumArtwork.coverView)
            Glide
                .with(binding.artistAvatar)
                .load(header.artist?.imageUrl)
                .placeholder(R.drawable.bg_vinyl_disc)
                .error(R.drawable.bg_vinyl_disc)
                .circleCrop()
                .into(binding.artistAvatar)
        }

        private fun bindInfo(header: AlbumDetailRow.Header) {
            val context = binding.root.context
            binding.labelInfo.infoLabel.setText(R.string.album_detail_label)
            binding.labelInfo.infoValue.text = header.recordLabel
            binding.dateInfo.infoLabel.setText(R.string.album_detail_release)
            binding.dateInfo.infoValue.text = header.releaseDate.format(dateFormatter)
            binding.tracksInfo.infoLabel.setText(R.string.album_detail_tracks)
            binding.tracksInfo.infoValue.text = context.getString(R.string.album_detail_count, header.trackCount)
        }
    }

    private class TrackHolder(
        private val binding: ItemAlbumDetailTrackBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: AlbumDetailRow.Song) {
            binding.trackNumber.text = binding.root.context.getString(R.string.album_detail_track_number, row.number)
            binding.trackName.text = row.track.name
            binding.trackDuration.text = row.track.duration
            binding.root.contentDescription =
                binding.root.context.getString(
                    R.string.album_detail_track_description,
                    row.number,
                    row.track.name,
                    row.track.duration,
                )
        }
    }

    private class CommentHolder(
        private val binding: ItemAlbumDetailCommentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: AlbumDetailRow.Review) {
            val context = binding.root.context
            val comment = row.comment
            val author = row.authorName ?: context.getString(R.string.album_detail_comment_author)
            binding.commentAuthor.text = author
            binding.commentAvatar.text = row.initials ?: author.take(1)
            binding.commentRating.rating = comment.rating.toFloat()
            binding.commentBody.text = comment.description
            // The card is read as one element: name, stars and text, not three separate stops.
            binding.root.contentDescription =
                context.getString(
                    R.string.album_detail_comment_description,
                    author,
                    comment.rating,
                    comment.description,
                )
            val stroke = if (row.isHighlighted) R.color.vinilos_charcoal else R.color.vinilos_line
            binding.root.strokeColor = ContextCompat.getColor(context, stroke)
            binding.root.strokeWidth =
                context.resources.getDimensionPixelSize(
                    if (row.isHighlighted) R.dimen.comment_card_stroke_highlight else R.dimen.list_divider_thickness,
                )
        }
    }

    private class SectionHolder(
        private val binding: ItemAlbumDetailSectionBinding,
        onCommentClick: () -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            ViewCompat.setAccessibilityHeading(binding.sectionTitle, true)
            binding.sectionAction.setOnClickListener { onCommentClick() }
        }

        fun bind(row: AlbumDetailRow.CommentsHeading) {
            binding.sectionAction.isVisible = row.canEdit
        }
    }

    private class MessageHolder(
        private val binding: ItemAlbumDetailMessageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: AlbumDetailRow.Message) {
            binding.detailMessage.setText(row.textRes)
            val style =
                if (row.isHeading) {
                    R.style.TextAppearance_Vinilos_AlbumTitle
                } else {
                    R.style.TextAppearance_Vinilos_AlbumDetailBody
                }
            TextViewCompat.setTextAppearance(
                binding.detailMessage,
                style,
            )
            ViewCompat.setAccessibilityHeading(binding.detailMessage, row.isHeading)
        }
    }

    private object RowDiff : DiffUtil.ItemCallback<AlbumDetailRow>() {
        override fun areItemsTheSame(
            oldItem: AlbumDetailRow,
            newItem: AlbumDetailRow,
        ): Boolean = oldItem.key == newItem.key

        override fun areContentsTheSame(
            oldItem: AlbumDetailRow,
            newItem: AlbumDetailRow,
        ): Boolean = oldItem == newItem
    }

    private companion object {
        const val HEADER = 0
        const val TRACK = 1
        const val COMMENT = 2
        const val MESSAGE = 3
        const val SECTION = 4
        const val DATE_PATTERN = "d MMM uuuu"
    }
}

internal sealed interface AlbumDetailRow {
    val key: String

    /**
     * Only what the header draws. A new comment or a highlight leaves it equal, so DiffUtil does not
     * bind it again; a new track changes [trackCount] and does.
     */
    data class Header(
        val name: String,
        val coverUrl: String,
        val genreName: String,
        val releaseDate: LocalDate,
        val performerName: String?,
        val description: String,
        val recordLabel: String,
        val trackCount: Int,
        val artist: PerformerSummary?,
    ) : AlbumDetailRow {
        override val key = "header"
    }

    data class Song(
        val number: Int,
        val track: Track,
    ) : AlbumDetailRow {
        override val key = "track:${track.id}"
    }

    /** [authorName] and [initials] are null while the author is unknown; the card then says "Coleccionista". */
    data class Review(
        val comment: Comment,
        val authorName: String?,
        val initials: String?,
        val isHighlighted: Boolean,
    ) : AlbumDetailRow {
        override val key = "comment:${comment.id}"
    }

    data class Message(
        val textRes: Int,
        val isHeading: Boolean = false,
    ) : AlbumDetailRow {
        override val key = "message:$textRes"
    }

    data class CommentsHeading(
        val canEdit: Boolean,
    ) : AlbumDetailRow {
        override val key = "comments-heading"
    }
}

internal fun Album.toHeaderRow(): AlbumDetailRow.Header =
    AlbumDetailRow.Header(
        name = name,
        coverUrl = coverUrl,
        genreName = genre.apiName,
        releaseDate = releaseDate,
        performerName = performerName,
        description = description,
        recordLabel = recordLabel,
        trackCount = tracks.size,
        artist = performers.firstOrNull(),
    )

// The initials arrive computed in the UiState, so building a row only copies values (docs/07).
private fun Comment.toRow(
    initials: String?,
    isHighlighted: Boolean,
): AlbumDetailRow.Review =
    AlbumDetailRow.Review(
        comment = this,
        authorName = author?.name,
        initials = initials,
        isHighlighted = isHighlighted,
    )
