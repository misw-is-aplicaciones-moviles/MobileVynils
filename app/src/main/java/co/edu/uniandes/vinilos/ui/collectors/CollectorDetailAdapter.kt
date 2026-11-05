package co.edu.uniandes.vinilos.ui.collectors

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailMessageBinding
import co.edu.uniandes.vinilos.databinding.ItemCollectorAlbumBinding
import co.edu.uniandes.vinilos.databinding.ItemCollectorContactBinding
import co.edu.uniandes.vinilos.databinding.ItemCollectorDetailHeaderBinding
import co.edu.uniandes.vinilos.databinding.ItemCollectorFavoriteBinding
import co.edu.uniandes.vinilos.databinding.ItemCollectorSectionBinding
import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.CollectorAlbum
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.viewmodels.CollectorDetailUiState
import co.edu.uniandes.vinilos.viewmodels.collectorInitials
import com.bumptech.glide.Glide

internal sealed interface CollectorDetailRow {
    data class Header(
        val initials: String,
        val name: String,
    ) : CollectorDetailRow

    data class Contact(
        val telephone: String,
        val email: String,
    ) : CollectorDetailRow

    data class Section(
        @param:StringRes val title: Int,
        val count: Int,
        @param:StringRes val action: Int,
        val canEdit: Boolean,
    ) : CollectorDetailRow

    data class Favorites(
        val favorites: List<PerformerSummary>,
    ) : CollectorDetailRow

    data class Album(
        val album: CollectorAlbum,
    ) : CollectorDetailRow

    data class Message(
        @param:StringRes val label: Int,
    ) : CollectorDetailRow
}

/** One vertical surface. Favorites form a horizontal strip inside it. */
internal class CollectorDetailAdapter(
    private val onAlbumClick: (Int) -> Unit,
    private val onFavoriteClick: (Int) -> Unit,
) : ListAdapter<CollectorDetailRow, RecyclerView.ViewHolder>(Rows) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    fun submitDetail(state: CollectorDetailUiState) {
        val detail = state.detail ?: return
        submitList(rowsOf(detail, state.canEdit))
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is CollectorDetailRow.Header -> HEADER
            is CollectorDetailRow.Contact -> CONTACT
            is CollectorDetailRow.Section -> SECTION
            is CollectorDetailRow.Favorites -> FAVORITES
            is CollectorDetailRow.Album -> ALBUM
            is CollectorDetailRow.Message -> MESSAGE
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            HEADER -> HeaderHolder(ItemCollectorDetailHeaderBinding.inflate(inflater, parent, false))
            CONTACT -> ContactHolder(ItemCollectorContactBinding.inflate(inflater, parent, false))
            SECTION -> SectionHolder(ItemCollectorSectionBinding.inflate(inflater, parent, false))
            FAVORITES -> FavoritesHolder(favoriteList(parent), onFavoriteClick)
            ALBUM -> AlbumHolder(ItemCollectorAlbumBinding.inflate(inflater, parent, false), onAlbumClick)
            MESSAGE -> MessageHolder(ItemAlbumDetailMessageBinding.inflate(inflater, parent, false))
            else -> error("Unknown collector detail row")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val row = getItem(position)) {
            is CollectorDetailRow.Header -> (holder as HeaderHolder).bind(row)
            is CollectorDetailRow.Contact -> (holder as ContactHolder).bind(row)
            is CollectorDetailRow.Section -> (holder as SectionHolder).bind(row)
            is CollectorDetailRow.Favorites -> (holder as FavoritesHolder).bind(row.favorites)
            is CollectorDetailRow.Album -> (holder as AlbumHolder).bind(row.album)
            is CollectorDetailRow.Message -> (holder as MessageHolder).bind(row.label)
        }
    }

    private class HeaderHolder(
        private val binding: ItemCollectorDetailHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: CollectorDetailRow.Header) {
            binding.collectorInitials.text = row.initials
            binding.collectorName.text = row.name
            ViewCompat.setAccessibilityHeading(binding.collectorName, true)
        }
    }

    private class ContactHolder(
        private val binding: ItemCollectorContactBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: CollectorDetailRow.Contact) {
            binding.collectorPhone.text = row.telephone
            binding.collectorEmail.text = row.email
        }
    }

    private class SectionHolder(
        private val binding: ItemCollectorSectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: CollectorDetailRow.Section) {
            binding.sectionTitle.text = binding.root.context.getString(row.title, row.count)
            ViewCompat.setAccessibilityHeading(binding.sectionTitle, true)
            binding.sectionAction.isVisible = row.canEdit
            binding.sectionAction.setText(row.action)
            binding.sectionAction.isEnabled = false
        }
    }

    private class FavoritesHolder(
        private val list: RecyclerView,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(list) {
        fun bind(favorites: List<PerformerSummary>) {
            list.adapter = FavoriteAdapter(favorites, onClick)
        }
    }

    private class AlbumHolder(
        private val binding: ItemCollectorAlbumBinding,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(album: CollectorAlbum) {
            val context = binding.root.context
            val status = context.getString(album.status.label)
            binding.albumTitle.text = album.name
            binding.albumPrice.text = context.getString(R.string.collector_album_price, album.price)
            binding.albumStatus.text = status
            binding.albumStatus.setBackgroundResource(album.status.background)
            binding.albumStatus.setTextColor(ContextCompat.getColor(context, album.status.textColor))
            binding.root.contentDescription =
                context.getString(R.string.collector_open_album, album.name, binding.albumPrice.text, status)
            binding.root.setOnClickListener { onClick(album.albumId) }
            binding.albumArtwork.coverView.loadImage(album.coverUrl, R.drawable.ic_nav_albums)
        }
    }

    private class MessageHolder(
        private val binding: ItemAlbumDetailMessageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            @StringRes label: Int,
        ) {
            binding.detailMessage.setText(label)
        }
    }

    private object Rows : DiffUtil.ItemCallback<CollectorDetailRow>() {
        private fun key(row: CollectorDetailRow): String =
            when (row) {
                is CollectorDetailRow.Header -> "header"
                is CollectorDetailRow.Contact -> "contact"
                is CollectorDetailRow.Section -> "section-${row.title}"
                is CollectorDetailRow.Favorites -> "favorites"
                is CollectorDetailRow.Album -> "album-${row.album.albumId}"
                is CollectorDetailRow.Message -> "message-${row.label}"
            }

        override fun areItemsTheSame(
            oldItem: CollectorDetailRow,
            newItem: CollectorDetailRow,
        ): Boolean = key(oldItem) == key(newItem)

        override fun areContentsTheSame(
            oldItem: CollectorDetailRow,
            newItem: CollectorDetailRow,
        ): Boolean = oldItem == newItem
    }

    private companion object {
        const val HEADER = 0
        const val CONTACT = 1
        const val SECTION = 2
        const val FAVORITES = 3
        const val ALBUM = 4
        const val MESSAGE = 5

        private fun rowsOf(
            detail: CollectorDetail,
            canEdit: Boolean,
        ): List<CollectorDetailRow> =
            buildList {
                val collector = detail.collector
                add(CollectorDetailRow.Header(collectorInitials(collector.name), collector.name))
                add(CollectorDetailRow.Contact(detail.telephone, collector.email))
                add(
                    section(
                        R.string.collector_section_favorites,
                        detail.favorites.size,
                        R.string.action_add_favorite,
                        canEdit,
                    ),
                )
                if (detail.favorites.isEmpty()) {
                    add(CollectorDetailRow.Message(R.string.collector_no_favorites))
                } else {
                    add(CollectorDetailRow.Favorites(detail.favorites))
                }
                add(section(R.string.collector_section_albums, detail.albums.size, R.string.action_add_album, canEdit))
                if (detail.albums.isEmpty()) {
                    add(CollectorDetailRow.Message(R.string.collector_no_albums))
                } else {
                    detail.albums.forEach { add(CollectorDetailRow.Album(it)) }
                }
            }

        private fun section(
            title: Int,
            count: Int,
            action: Int,
            canEdit: Boolean,
        ) = CollectorDetailRow.Section(title, count, action, canEdit)

        private fun favoriteList(parent: ViewGroup): RecyclerView =
            RecyclerView(parent.context).apply {
                id = R.id.collectorFavorites
                layoutParams =
                    RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                clipChildren = false
                clipToPadding = false
            }
    }
}

private class FavoriteAdapter(
    private val favorites: List<PerformerSummary>,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<FavoriteHolder>() {
    override fun getItemCount(): Int = favorites.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ) = FavoriteHolder(
        ItemCollectorFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onClick,
    )

    override fun onBindViewHolder(
        holder: FavoriteHolder,
        position: Int,
    ) = holder.bind(favorites[position])
}

private class FavoriteHolder(
    private val binding: ItemCollectorFavoriteBinding,
    private val onClick: (Int) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(favorite: PerformerSummary) {
        val context = binding.root.context
        val typeLabel =
            if (favorite.type == PerformerType.BAND) R.string.artist_band else R.string.artist_musician
        val type = context.getString(typeLabel)
        binding.favoriteName.text = favorite.name
        binding.favoriteType.text = type
        binding.favoriteArtwork.performerImage.loadImage(favorite.imageUrl, R.drawable.bg_performer_image_placeholder)
        binding.root.contentDescription = context.getString(R.string.collector_open_favorite, favorite.name, type)
        binding.root.setOnClickListener { onClick(favorite.id) }
    }
}

private val AlbumStatus.label: Int
    get() = if (this == AlbumStatus.ACTIVE) R.string.album_status_available else R.string.album_status_unavailable

private val AlbumStatus.background: Int
    get() =
        if (this == AlbumStatus.ACTIVE) {
            R.drawable.bg_album_status_available
        } else {
            R.drawable.bg_album_status_unavailable
        }

private val AlbumStatus.textColor: Int
    get() = if (this == AlbumStatus.ACTIVE) R.color.vinilos_cream else R.color.vinilos_charcoal

private fun ImageView.loadImage(
    url: String?,
    placeholder: Int,
) {
    Glide
        .with(this)
        .load(url)
        .placeholder(placeholder)
        .error(placeholder)
        .centerCrop()
        .into(this)
}
