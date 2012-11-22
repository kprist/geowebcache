package org.geowebcache.layer.gwc;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.FileResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;
import org.geowebcache.storage.blobstore.file.FilePathUtils;

/**
 * 
 * @author Gabriel Roldan
 * 
 */
/**
 * @author kprist
 * 
 */
public class GWCCacheLayer extends AbstractTileLayer {

	private static final Log log = LogFactory.getLog(GWCCacheLayer.class);

	/**
	 * Location of the actual tiles folder.
	 * <p>
	 * 
	 * Note: Could be optional, defaulting to a directory under the default
	 * caching directory with the same name as {@link #getName()}. This default
	 * may or may not be the same as the blobstore root path, depending on
	 * configuration.
	 */
	private File tileCachePath;

	/**
	 * Returns the location of the actual tiles folder.
	 * <p>
	 * 
	 * Note: maybe this could be optional, returning {@code null} if not
	 * provided, in which case defaults internally to a directory under the
	 * default caching directory with the same name as {@link #getName()}. This
	 * default may or may not be the same as the blobstore root path, depending
	 * on configuration.
	 */
	public File getTileCachePath() {
		return tileCachePath;
	}

	/**
	 * Options, location of the actual tiles folder. If not provided defaults to
	 * the {@code _alllayers} directory at the same location than the
	 * {@link #getTilingScheme() conf.xml} tiling scheme.
	 */
	public void setTileCachePath(File tileCachePath) {
		this.tileCachePath = tileCachePath;
	}

	/**
	 * @see org.geowebcache.layer.TileLayer#initialize(org.geowebcache.grid.GridSetBroker)
	 * @return {@code true} if success. Note this method's return type should be
	 *         void. It's not checked anywhere
	 */
	@Override
	protected boolean initializeInternal(GridSetBroker gridSetBroker) {
		if (super.enabled == null) {
			super.enabled = true;
		}
		if (tileCachePath == null) {
			throw new IllegalStateException(
					"tileCachePath has not been set. It should point to the directory containing the tiles");

		} else {
			if (!tileCachePath.exists() || !tileCachePath.isDirectory()
					|| !tileCachePath.canRead()) {
				throw new IllegalStateException(
						"tileCachePath property for layer '"
								+ getName()
								+ "' is set to '"
								+ tileCachePath
								+ "' but the directory either does not exist or is not readable");
			}
		}

		log.info("Configuring layer " + getName() + " stored in  "
				+ tileCachePath == null ? "null" : tileCachePath
				.getAbsolutePath());

		// super.formats = loadMimeTypes();
		return true;
	}

	private List<MimeType> loadMimeTypes() {
		// TODO: search cache structure for image types by extension
		MimeType format;
		try {
			format = MimeType.createFromFormat("image/png");
		} catch (MimeException e) {
			throw new RuntimeException(e);
		}
		return Collections.singletonList(format);
	}

	/**
	 * 
	 * @see org.geowebcache.layer.TileLayer#getTile(org.geowebcache.conveyor.ConveyorTile)
	 */
	@Override
	public ConveyorTile getTile(final ConveyorTile tile)
			throws GeoWebCacheException, IOException, OutsideCoverageException {

		File tileFile = tilePath(tile);

		if (tileFile.exists()) {
			Resource tileContent = readFile(tileFile);
			tile.setCacheResult(CacheResult.HIT);
			tile.setBlob(tileContent);
		} else {
			tile.setCacheResult(CacheResult.MISS);
			if (!setLayerBlankTile(tile)) {
				throw new OutsideCoverageException(tile.getTileIndex(), 0, 0);
			}
		}
		return tile;
	}

	private boolean setLayerBlankTile(ConveyorTile tile) {
		// this is off specs, maybe a configuartion option for this
		// specifically?
		// TODO cache result
		String layerPath = getLayerPath().append(File.separatorChar).toString();
		File png = new File(layerPath + "blank.png");
		Resource blank = null;
		try {
			if (png.exists()) {
				blank = readFile(png);
				tile.setBlob(blank);
				tile.setMimeType(MimeType.createFromFormat("image/png"));
			} else {
				File jpeg = new File(layerPath + "missing.jpg");
				if (jpeg.exists()) {
					blank = readFile(jpeg);
					tile.setBlob(blank);
					tile.setMimeType(MimeType.createFromFormat("image/jpeg"));
				}
			}
		} catch (Exception e) {
			return false;
		}
		return blank != null;
	}

	/**
	 * Builds the storage path for a tile and returns it as a File reference.
	 * <p>
	 * Copied from {@link FilePathGenerator#tilePath(TileObject, MimeType)}, but
	 * has changes for correct (or no) padding
	 * 
	 * @param tile
	 * @return File pointer to the tile image
	 */
	private File tilePath(final ConveyorTile tile) {
		final long[] tileIndex = tile.getTileIndex();

		final long x = tileIndex[0];
		final long y = tileIndex[1];
		// should I invert this in some cases???

		// final String gridSetId = tile.getGridSetId();
		// final GridSubset gridSubset = this.getGridSubset(gridSetId);
		// GridSet gridSet = gridSubset.getGridSet();
		// Grid grid = gridSet.getGridLevels()[z];
		// long coverageMaxY = grid.getNumTilesHigh() - 1;
		// final long y = (coverageMaxY - tileIndex[1]);
		final int z = (int) tileIndex[2];

		StringBuilder path = getLayerPath();

		long shift = z / 2;
		long half = 2 << shift;
		int digits = 1;
		if (half > 10) {
			digits = (int) (Math.log10(half)) + 1;
		}
		long halfx = x / half;
		long halfy = y / half;

		path.append(File.separatorChar);

		appendGridsetZoomLevelDir(tile.getGridSetId(), z, path);

		String parametersId = tile.getParametersId();
		Map<String, String> parameters = tile.getParameters();
		if (parametersId == null && parameters != null && !parameters.isEmpty()) {
			parametersId = FilePathGenerator.getParametersId(parameters);
			// what to do with this?
			// tile.setParametersId(parametersId); //original
			tile.getStorageObject().setParametersId(parametersId);
		}
		if (parametersId != null) {
			path.append('_');
			path.append(parametersId);
		}
		path.append(File.separatorChar);

		zeroPadder(halfx, digits, path);
		path.append('_');
		zeroPadder(halfy, digits, path);
		path.append(File.separatorChar);

		zeroPadder(x, 2 * digits, path);
		path.append('_');
		zeroPadder(y, 2 * digits, path);
		path.append('.');

		final MimeType mimeType = tile.getMimeType();
		String fileExtension = mimeType.getFileExtension();
		path.append(fileExtension);

		return new File(path.toString());
	}

	private static void appendGridsetZoomLevelDir(String gridSetId, long z,
			StringBuilder path) {
		FilePathUtils.appendFiltered(gridSetId, path);
		path.append('_');
		zeroPadder(z, 2, path);
	}

	private static void zeroPadder(long number, int order, StringBuilder padding) {
		FilePathUtils.zeroPadder(number, order, padding);
	}

	private static void zeroPadder3(long number, int order,
			StringBuilder padding) {
		int numberOrder = stringSizeOfLong(number);
		int diffOrder = order - numberOrder;
		if (diffOrder > 0) {
			while (diffOrder > 0) {
				padding.append('0');
				diffOrder--;
			}
		}
		padding.append(number);
	}

	// positive x only!
	private static int stringSizeOfLong(long x) {
		long p = 10;
		for (int i = 1; i < 19; i++) {
			if (x < p)
				return i;
			p = 10 * p;
		}
		return 19;
	}

	private StringBuilder getLayerPath() {
		StringBuilder path;
		path = new StringBuilder(tileCachePath.getAbsolutePath());
		return path;
	}

	private Resource readFile(File fh) {
		if (!fh.exists()) {
			return null;
		}
		Resource res = new FileResource(fh);
		return res;
	}

	/**
	 * 
	 * @see org.geowebcache.layer.TileLayer#getNoncachedTile(org.geowebcache.conveyor.ConveyorTile)
	 */
	@Override
	public ConveyorTile getNoncachedTile(ConveyorTile tile)
			throws GeoWebCacheException {
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @see org.geowebcache.layer.TileLayer#seedTile(org.geowebcache.conveyor.ConveyorTile,
	 *      boolean)
	 */
	@Override
	public void seedTile(ConveyorTile tile, boolean tryCache)
			throws GeoWebCacheException, IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @see org.geowebcache.layer.TileLayer#doNonMetatilingRequest(org.geowebcache.conveyor.ConveyorTile)
	 */
	@Override
	public ConveyorTile doNonMetatilingRequest(ConveyorTile tile)
			throws GeoWebCacheException {
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @see org.geowebcache.layer.TileLayer#getStyles()
	 */
	@Override
	public String getStyles() {
		return null;
	}

	/**
	 * 
	 * @see org.geowebcache.layer.TileLayer#setExpirationHeader(javax.servlet.http.HttpServletResponse,
	 *      int)
	 */
	@Override
	public void setExpirationHeader(HttpServletResponse response, int zoomLevel) {
		/*
		 * NOTE: this method doesn't seem like belonging to TileLayer, but to
		 * GeoWebCacheDispatcher itself
		 */
		return;
	}

}
