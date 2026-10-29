package co.edu.uniandes.vinilos.ui.albums

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemAlbumCatalogBinding
import co.edu.uniandes.vinilos.viewmodels.AlbumItem
import com.bumptech.glide.Glide

/** Catalog rows. A tap reports the album id; the adapter never touches data or navigation. */
class AlbumAdapter(
    private val onAlbumClick: (Int) -> Unit,
) : ListAdapter<AlbumItem, AlbumViewHolder>(AlbumItemDiff) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): AlbumViewHolder {
        val binding = ItemAlbumCatalogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumViewHolder(binding, onAlbumClick)
    }

    override fun onBindViewHolder(
        holder: AlbumViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }
}

class AlbumViewHolder(
    private val binding: ItemAlbumCatalogBinding,
    private val onAlbumClick: (Int) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: AlbumItem) {
        val context = binding.root.context
        val artist = item.artistName ?: context.getString(R.string.album_no_artist)
        val meta = context.getString(R.string.album_genre_year, item.genreName, item.year)
        binding.albumNumber.text = item.position
        binding.albumTitle.text = item.name
        binding.albumSubtitle.text = artist
        binding.albumMeta.text = meta
        // The row is a single accessibility node: its children are hidden from TalkBack.
        binding.root.contentDescription = context.getString(R.string.album_item_description, item.name, artist, meta)
        binding.root.setOnClickListener { onAlbumClick(item.id) }
        Glide
            .with(binding.root)
            .load(item.coverUrl)
            .placeholder(R.drawable.img_album_cover)
            .error(R.drawable.img_album_cover)
            .centerCrop()
            .into(binding.albumArtwork.coverView)
    }
}

private object AlbumItemDiff : DiffUtil.ItemCallback<AlbumItem>() {
    override fun areItemsTheSame(
        oldItem: AlbumItem,
        newItem: AlbumItem,
    ): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(
        oldItem: AlbumItem,
        newItem: AlbumItem,
    ): Boolean = oldItem == newItem
}
