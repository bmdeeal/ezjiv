package noTLD.bmdeeal.ezjiv;
import java.io.*;
import javax.imageio.*;

/*
FileInfo is not a nice, publicly designed class.
It is an ad-hoc mess designed entirely to make a few things easier in ezjiv.
*/

public class FileInfo {
	
	public static String getExtension(File f) {
		String fileName = f.getName();
		int dotPosition = fileName.lastIndexOf(".");
		if (dotPosition==-1) {
			return "";
		}
		return fileName.substring(dotPosition+1);
	}
	
	public static String getNameNoExtension(File f) {
		String fileName = f.getName();
		int dotPosition = fileName.lastIndexOf(".");
		if (dotPosition==-1) {
			return fileName;
		}
		return fileName.substring(0,dotPosition);
	}
	
	public static FileInfo[] toFileInfo(File[] f) {
		FileInfo[] result = new FileInfo[f.length];
		for (int ii=0; ii<f.length; ii++) {
			result[ii] = new FileInfo(f[ii]);
			
		}
		return result;
	}
	
	public static boolean accept(File p) {
		String ext = getExtension(p).toLowerCase();
		for (int ii=0; ii<formats.length; ii++) {
			if (formats[ii].equals(ext)) {
				return true;
			}
		}
		return false;
	}
	
	enum SortMode {
		FILENAME,
		FULLNAME,
		SIZE,
		DATE;
	}
	
	//please don't set any of these directly
	//I'll get around to doing getters for them at some point, but not now
	File file;
	static SortMode mode=SortMode.FULLNAME;
	long timestamp;
	long size;
	String fullName;
	String name;
	String extension;
	public static final String[] formats = {
		"jpg",
		"jpeg",
		"png",
		"gif"
	};
	
	//this class pulls out interesting info from the File object so you don't need to do it yourself
	public FileInfo(File f) {
		file=f;
		timestamp=file.lastModified();
		size=file.length();
		extension = getExtension(f);
		name = getNameNoExtension(f);
		fullName=f.getName();
	}
	
	//comparators
	//just do something like Arrays.sort(target, (FileInfo a, FileInfo b)->a.compareWhatever(b)); to use
	//sort by name
	public int compareName(FileInfo other) {
		return name.compareTo(other.name);
	}
	//sort by date
	public int compareTimestamp(FileInfo other) {
		return Long.compare(timestamp, other.timestamp);
	}
	//sort by size
	public int compareSize(FileInfo other) {
		return Long.compare(size, other.size);
	}
	//sort by extension, and then by name
	public int compareExtension(FileInfo other) {
		int c;
		c=extension.compareTo(other.extension);
		if (c!=0) {
			return c;
		}
		return compareName(other);
	}
}