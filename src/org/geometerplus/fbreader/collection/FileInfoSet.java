/*
 * Copyright (C) 2009 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.collection;

import java.util.*;

import org.geometerplus.zlibrary.core.util.ZLMiscUtil;
import org.geometerplus.zlibrary.core.filesystem.*;

public final class FileInfoSet {
	private static final class Pair {
		private final String myName;
		private final FileInfo myParent;

		Pair(String name, FileInfo parent) {
			myName = name;
			myParent = parent;
		}

		@Override
		public int hashCode() {
			return (myParent == null) ? myName.hashCode() : myParent.hashCode() + myName.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Pair)) {
				return false;
			}
			Pair p = (Pair)o;
			return (myName.equals(p.myName)) && ZLMiscUtil.equals(myParent, p.myParent);
		}
	}

	private final HashMap<Pair,FileInfo> myInfosByPair = new HashMap<Pair,FileInfo>();
	private final LinkedHashSet<FileInfo> myInfosToSave = new LinkedHashSet<FileInfo>();
	private final LinkedHashSet<FileInfo> myInfosToRemove = new LinkedHashSet<FileInfo>();

	private void load(Collection<FileInfo> infos) {
		for (FileInfo info : infos) {
			myInfosByPair.put(new Pair(info.Name, info.Parent), info);
		}
		System.err.println(myInfosByPair.size() + " infos have been loaded");
	}

	public void loadAll() {
		load(BooksDatabase.Instance().loadFileInfos());
	}

	public void load(ZLFile file) {
		load(BooksDatabase.Instance().loadFileInfos(file));
	}

	public void save() {
		System.err.println("infos " + myInfosByPair.size());
		System.err.println("to save " + myInfosToSave.size());
		System.err.println("to remove " + myInfosToRemove.size());
		final BooksDatabase database = BooksDatabase.Instance();
		database.executeAsATransaction(new Runnable() {
			public void run() {
				for (FileInfo info : myInfosToRemove) {
					database.removeFileInfo(info.Id);
					myInfosByPair.remove(new Pair(info.Name, info.Parent));
				}
				myInfosToRemove.clear();
				for (FileInfo info : myInfosToSave) {
					database.saveFileInfo(info);
				}
				myInfosToSave.clear();
			}
		});
	}

	public boolean check(ZLPhysicalFile file) {
		if (file == null) {
			return true;
		}
		final long fileSize = file.size();
		FileInfo info = get(file);
		if (info.FileSize == fileSize) {
			return true;
		} else {
			info.FileSize = fileSize;
			removeChildren(info);
			myInfosToSave.add(info);
			addChildren(file);
			return false;
		}
	}

	public List<ZLFile> archiveEntries(ZLFile file) {
		final FileInfo info = get(file);
		if (!info.hasChildren()) {
			return Collections.emptyList();
		}
		final LinkedList<ZLFile> entries = new LinkedList<ZLFile>();
		for (FileInfo child : info.subTrees()) {
			if (!myInfosToRemove.contains(child)) {
				entries.add(ZLArchiveEntryFile.createArchiveEntryFile(file, child.Name));
			}
		}
		return entries;
	}

	private FileInfo get(String name, FileInfo parent) {
		final Pair pair = new Pair(name, parent);
		FileInfo info = myInfosByPair.get(pair);
		if (info == null) {
			info = new FileInfo(name, parent);
			myInfosByPair.put(pair, info);
			myInfosToSave.add(info);
		}
		return info;
	}

	private FileInfo get(ZLFile file) {
		return (file == null) ? null : get(file.getName(false), get(file.getParent()));
	}

	private void removeChildren(FileInfo info) {
		for (FileInfo child : info.subTrees()) {
			if (myInfosToSave.contains(child)) {
				myInfosToSave.remove(child);
			} else {
				myInfosToRemove.add(child);
			}
			removeChildren(child);
		}
	}

	private void addChildren(ZLFile file) {
		for (ZLFile child : file.children()) {
			final FileInfo info = get(child);
			if (myInfosToRemove.contains(info)) {
				myInfosToRemove.remove(info);
			} else {
				myInfosToSave.add(info);
			}
			addChildren(child);
		}
	}
}