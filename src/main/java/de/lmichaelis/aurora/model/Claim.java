// Copyright (c) 2021. Luis Michaelis
// SPDX-License-Identifier: LGPL-3.0-only
package de.lmichaelis.aurora.model;

import com.j256.ormlite.dao.ForeignCollection;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;
import de.lmichaelis.aurora.Aurora;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a claim or sub-claim owned by a player. Contains the claim's
 * configuration values.
 */
@DatabaseTable(tableName = "claims")
public final class Claim {
	@DatabaseField(generatedId = true)
	public int id;

	@DatabaseField
	public String name;

	@DatabaseField(canBeNull = false)
	public Date createdAt;

	@DatabaseField(canBeNull = false, columnName = "min_x", index = true, indexName = "claims_location_idx")
	public int minX;

	@DatabaseField(canBeNull = false, columnName = "min_y", index = true, indexName = "claims_location_idx")
	public int minY;

	@DatabaseField(canBeNull = false, columnName = "min_z", index = true, indexName = "claims_location_idx")
	public int minZ;

	@DatabaseField(canBeNull = false, columnName = "max_x", index = true, indexName = "claims_location_idx")
	public int maxX;

	@DatabaseField(canBeNull = false, columnName = "max_y", index = true, indexName = "claims_location_idx")
	public int maxY;

	@DatabaseField(canBeNull = false, columnName = "max_z", index = true, indexName = "claims_location_idx")
	public int maxZ;

	@DatabaseField(columnName = "parent_id", foreign = true, index = true, indexName = "claims_parent_idx")
	public Claim parent;

	@DatabaseField(canBeNull = false)
	public UUID owner;

	@DatabaseField(canBeNull = false, width = 64, index = true, indexName = "claims_location_idx")
	public String world;

	@ForeignCollectionField(foreignFieldName = "claim", eager = true)
	private ForeignCollection<UserGroup> userGroups;

	private final HashMap<UUID, UserGroup> userGroupById = new HashMap<>();

	public Claim(final @NotNull UUID owner, final @NotNull String name,
				 final @NotNull Location cornerA, final @NotNull Location cornerB) {
		this.owner = owner;
		this.name = name;
		this.world = cornerA.getWorld().getName();

		this.minX = Math.min(cornerA.getBlockX(), cornerB.getBlockX());
		this.minY = Math.min(cornerA.getBlockY(), cornerB.getBlockY());
		this.minZ = Math.min(cornerA.getBlockZ(), cornerB.getBlockZ());
		this.maxX = Math.max(cornerA.getBlockX(), cornerB.getBlockX());
		this.maxY = Math.max(cornerA.getBlockY(), cornerB.getBlockY());
		this.maxZ = Math.max(cornerA.getBlockZ(), cornerB.getBlockZ());
	}

	public Claim(final @NotNull Claim parent, final @NotNull Location cornerA, final @NotNull Location cornerB) {
		this.parent = parent;
		this.owner = parent.owner;
		this.world = parent.world;

		this.minX = Math.min(cornerA.getBlockX(), cornerB.getBlockX());
		this.minY = Math.min(cornerA.getBlockY(), cornerB.getBlockY());
		this.minZ = Math.min(cornerA.getBlockZ(), cornerB.getBlockZ());
		this.maxX = Math.max(cornerA.getBlockX(), cornerB.getBlockX());
		this.maxY = Math.max(cornerA.getBlockY(), cornerB.getBlockY());
		this.maxZ = Math.max(cornerA.getBlockZ(), cornerB.getBlockZ());
	}

	@SuppressWarnings("ProtectedMemberInFinalClass")
	protected Claim() {
	}

	/**
	 * Gets the claim at the given location.
	 *
	 * @param location The location to query a claim for.
	 * @return A claim if there is one at the given location and <tt>null</tt> if not.
	 */
	public static @Nullable Claim getClaim(final @NotNull Location location) {
		// TODO: When regions are loaded, query the database for claims in the region and cache them.
		//       Don't unload them from the cache. If we want to find a chunk, iterate through all chunks
		//       of the region of the interaction.
		try {
			return Aurora.db.claims.queryBuilder().where()
					.eq("world", location.getWorld().getName()).and()
					.le("min_x", location.getBlockX()).and()
					.ge("max_x", location.getBlockX()).and()
					.le("min_y", location.getBlockY()).and()
					.ge("max_y", location.getBlockY()).and()
					.le("min_z", location.getBlockZ()).and()
					.ge("max_z", location.getBlockZ())
					.queryBuilder()
					.orderByNullsLast("parent_id", false)
					.queryForFirst();
		} catch (SQLException e) {
			Aurora.logger.fatal("Failed to get claim at", e);
			return null;
		}
	}

	/**
	 * Saves the claim into the database.
	 *
	 * @return <tt>true</tt> if saving the claim was successful, <tt>false</tt> if not.
	 */
	public boolean save() {
		try {
			Aurora.db.claims.create(this);
			return true;
		} catch (SQLException e) {
			Aurora.logger.error("Failed to create a claim", e);
			return false;
		}
	}

	/**
	 * Updates the claim in the database.
	 *
	 * @return <tt>true</tt> if updating the claim was successful, <tt>false</tt> if not.
	 */
	public boolean update() {
		try {
			Aurora.db.claims.update(this);
			return true;
		} catch (SQLException e) {
			Aurora.logger.error("Failed to update a claim", e);
			return false;
		}
	}

	/**
	 * Deletes the claim from the database.
	 *
	 * @return <tt>true</tt> if deleting the claim was successful, <tt>false</tt> if not.
	 */
	public boolean delete() {
		try {
			Aurora.db.claims.delete(this);
			return true;
		} catch (SQLException e) {
			Aurora.logger.error("Failed to delete a claim", e);
			return false;
		}
	}

	/**
	 * Sets the given group for the given player in the claim.
	 *
	 * @param player The player to set the group for.
	 * @param group  The group to set.
	 */
	public boolean setGroup(final @NotNull OfflinePlayer player, final Group group) {
		var userGroup = userGroupById.getOrDefault(player.getUniqueId(), null);

		try {
			if (userGroup != null) {
				userGroup.group = group;
				Aurora.db.userGroups.update(userGroup);
			} else {
				userGroup = new UserGroup(this, player.getUniqueId(), group);
				Aurora.db.userGroups.create(userGroup);
				Aurora.db.claims.refresh(this);
				this.userGroupById.put(player.getUniqueId(), userGroup);
			}
		} catch (SQLException e) {
			Aurora.logger.error("Failed to set a player group", e);
			return false;
		}

		return true;
	}

	/**
	 * Gets the group the given player is in.
	 *
	 * @param player The player to get the group for.
	 * @return The group the player is in.
	 */
	public Group getGroup(final @NotNull OfflinePlayer player) {
		if (Objects.equals(player.getUniqueId(), this.owner)) return Group.OWNER;
		if (this.userGroups.size() == 0) return Group.NONE;

		if (this.userGroupById.isEmpty()) {
			for (final var group : this.userGroups)
				this.userGroupById.put(group.player, group);
		}

		final var group = this.userGroupById.get(player.getUniqueId());
		return group == null ? Group.NONE : group.group;
	}

	public boolean isAllowed(final @NotNull OfflinePlayer player, final Group group) {
		return getGroup(player).encompasses(group);
	}

	/**
	 * Checks whether the given location is inside the claim.
	 *
	 * @param location The location to check.
	 * @return <tt>true</tt> if the location is in the claim and <tt>false</tt> if it is not.
	 */
	public boolean contains(final @NotNull Location location) {
		return location.getBlockX() >= minX &&
				location.getBlockX() <= maxX &&
				location.getBlockY() >= minY &&
				location.getBlockY() <= maxY &&
				location.getBlockZ() >= minZ &&
				location.getBlockZ() <= maxZ &&
				Objects.equals(location.getWorld().getName(), world);
	}
}