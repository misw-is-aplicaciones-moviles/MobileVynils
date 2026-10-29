package co.edu.uniandes.vinilos.ui.albums

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailCommentBinding
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailHeaderBinding
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailMessageBinding
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailTrackBinding
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.Track
import com.bumptech.glide.Glide
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One scrolling surface for the album header, section labels, tracks and comments. */
internal class AlbumDetailAdapter : ListAdapter<AlbumDetailRow, RecyclerView.ViewHolder>(RowDiff) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    fun submitAlbum(album: Album) {
        submitList(
            buildList {
                add(AlbumDetailRow.Header(album))
                add(AlbumDetailRow.Message(R.string.album_detail_tracks, isHeading = true))
                album.tracks.forEachIndexed { index, track -> add(AlbumDetailRow.Song(index + 1, track)) }
                if (album.tracks.isEmpty()) add(AlbumDetailRow.Message(R.string.album_detail_no_tracks))
                add(AlbumDetailRow.Message(R.string.album_detail_comments, isHeading = true))
                album.comments.forEach { add(AlbumDetailRow.Review(it)) }
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
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            HEADER -> HeaderHolder(ItemAlbumDetailHeaderBinding.inflate(inflater, parent, false))
            TRACK -> TrackHolder(ItemAlbumDetailTrackBinding.inflate(inflater, parent, false))
            COMMENT -> CommentHolder(ItemAlbumDetailCommentBinding.inflate(inflater, parent, false))
            MESSAGE -> MessageHolder(ItemAlbumDetailMessageBinding.inflate(inflater, parent, false))
            else -> error("Unknown album detail row")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val row = getItem(position)) {
            is AlbumDetailRow.Header -> (holder as HeaderHolder).bind(row.album)
            is AlbumDetailRow.Song -> (holder as TrackHolder).bind(row)
            is AlbumDetailRow.Review -> (holder as CommentHolder).bind(row.comment)
            is AlbumDetailRow.Message -> (holder as MessageHolder).bind(row)
        }
    }

    private class HeaderHolder(
        private val binding: ItemAlbumDetailHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(album: Album) {
            val context = binding.root.context
            binding.albumName.text = album.name
            ViewCompat.setAccessibilityHeading(binding.albumName, true)
            binding.albumGenreYear.text =
                context.getString(R.string.album_genre_year, album.genre.apiName, album.releaseDate.year)
            binding.albumArtist.text = album.performerName ?: context.getString(R.string.album_no_artist)
            binding.albumDescription.text = album.description
            bindArtwork(album)
            bindInfo(album)
        }

        private fun bindArtwork(album: Album) {
            Glide
                .with(binding.albumArtwork.coverView)
                .load(album.coverUrl)
                .placeholder(R.drawable.ic_nav_albums)
                .error(R.drawable.ic_nav_albums)
                .centerCrop()
                .into(binding.albumArtwork.coverView)
            Glide
                .with(binding.artistAvatar)
                .load(album.performers.firstOrNull()?.imageUrl)
                .placeholder(R.drawable.bg_vinyl_disc)
                .error(R.drawable.bg_vinyl_disc)
                .circleCrop()
                .into(binding.artistAvatar)
        }

        private fun bindInfo(album: Album) {
            val context = binding.root.context
            binding.labelInfo.infoLabel.setText(R.string.album_detail_label)
            binding.labelInfo.infoValue.text = album.recordLabel
            binding.dateInfo.infoLabel.setText(R.string.album_detail_release)
            binding.dateInfo.infoValue.text =
                album.releaseDate.format(
                    DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault()),
                )
            binding.tracksInfo.infoLabel.setText(R.string.album_detail_tracks)
            binding.tracksInfo.infoValue.text = context.getString(R.string.album_detail_count, album.tracks.size)
        }
    }

    private class TrackHolder(
        private val binding: ItemAlbumDetailTrackBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: AlbumDetailRow.Song) {
            binding.trackNumber.text = binding.root.context.getString(R.string.album_detail_track_number, row.number)
            binding.trackName.text = row.track.name
            binding.trackDuration.text = row.track.duration
        }
    }

    private class CommentHolder(
        private val binding: ItemAlbumDetailCommentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: Comment) {
            val context = binding.root.context
            val author = context.getString(R.string.album_detail_comment_author)
            binding.commentAuthor.text = author
            binding.commentAvatar.text = author.take(1)
            binding.commentRating.rating = comment.rating.toFloat()
            binding.commentRating.contentDescription = context.getString(R.string.album_detail_rating, comment.rating)
            binding.commentBody.text = comment.description
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
        const val DATE_PATTERN = "d MMM uuuu"
    }
}

internal sealed interface AlbumDetailRow {
    val key: String

    data class Header(
        val album: Album,
    ) : AlbumDetailRow {
        override val key = "header"
    }

    data class Song(
        val number: Int,
        val track: Track,
    ) : AlbumDetailRow {
        override val key = "track:${track.id}"
    }

    data class Review(
        val comment: Comment,
    ) : AlbumDetailRow {
        override val key = "comment:${comment.id}"
    }

    data class Message(
        val textRes: Int,
        val isHeading: Boolean = false,
    ) : AlbumDetailRow {
        override val key = "message:$textRes"
    }
}
