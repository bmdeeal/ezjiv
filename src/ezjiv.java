package noTLD.bmdeeal.ezjiv;

import javax.swing.*;
import java.util.*;
import javax.swing.filechooser.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;
import java.awt.event.*;
import javax.swing.plaf.metal.*;
import java.io.*;
import javax.imageio.*;
import java.net.URI;
import java.net.URL;

//I'm totally going to need to do a complete rewrite after I'm done, but alas.
//It's been a pretty reasonable learning experience, at least.

/*
ezjiv -- easy java image viewer
(C) 2019 B.M.Deeal

This program is provided under the terms of the GPL 2.0 or GPL 3.0.
The GPL 2.0 can be found in either LICENSE-2.0.txt provided with ezjiv, or <https://www.gnu.org/licenses/gpl-2.0.txt>.
The GPL 3.0 can be found in either LICENSE-3.0.txt provided with ezjiv, or <https://www.gnu.org/licenses/gpl-3.0.txt>.


it views images, lets you sort (todo) by name, by date, by size, by extension+name, and randomly
it'll let you copy the path of the file to the clipboard (todo)
it'll have rotate (without needing to save) and scale options (todo)

current todo list (relatively easy stuff):
* view the whole file list and pick a file (some kind of togglable left sidebar?)
* load image from command line (this one is really easy, I just need to do it)
* dragging to pan the image
* selectable theme and look+feel (really, just look at one of the Java built-in demos to see how it's done)
* maybe mousewheel scaling? (dunno, but at the least I should have finer options than just x2 zoom in/out)
* control bar (eg, set zoom percent directly, file info, memory use, etc)
* configuration (eg, look and feel, background color, default sort, default zoom, etc)
* on-screen image rotation (maybe easy?)
* allow user to copy path to clipboard

very far out todo (harder stuff, some of which might just need a good rewrite to do):
* progress bars for very slow operations (eg, dealing with a 10k+ image folder, something with swingworker or whatever)
* speed up -- it's like half the speed of feh, which I guess isn't too bad, but it makes a huge difference when I do testing for low-performance hardware via my raspi 2
* custom file picker (thumbnails? faster than the Java one? honestly, for ALL look and feels, the Java file picker is pretty garbage and feels really non-native anyway; this might just end up being its own project to write a good file picker)
* custom gif renderer (the built-in Java one screws up on a lot of gifs I have... and this might end up being its own project, or I'll just find one)
* support for a few other formats (the netpbm ones would be nice, and that one suckless format made to replace them if only because it's so dirt simple)
* format conversion maybe? (probably really easy, Java can save images)
* how images are scaled needs a total rewrite (don't scale the whole image all at once in memory, the visible portion should be scaled, I might just do away with using JScrollArea entirely and have it crop to view and rescale the image as you drag)

known bugs:
* if you name a folder with an extension (eg, .png), it'll still try and load it like a file (easy fix, just need to do it)
* scaling images up leads to crazy memory use and Java can run out of memory -- what I really need to do is only scale the visible portion of an image, and I'll need to do my own custom Scrollable object too
* at least until doing a full rewrite, I should probably set a maximum scaled size limit since zooming in 2x every time leads to absurd memory use
* it freaks out on a lot of GIF files (this isn't strictly my fault, blame Java's GIF decoder not matching how every major web browser handles GIFs)
* there's no real error handling -- there's more than a few things that can go wrong and I just catch Exception directly and show an error, I REALLY need to go look through what could happen and fix that
* the viewport isn't really positioned sensibly when loading a new image (well, I guess -- it's moot now that the viewer defaults to scaling to view size, but it'll still be an issue if I ever change that)

*/

/*
ideally, my scaling code should probably resemble this
//https://community.oracle.com/docs/DOC-983611
private float xScaleFactor, yScaleFactor = ...;
private BufferedImage originalImage = ...;
public void paintComponent(Graphics g) {
	Graphics2D g2 = (Graphics2D)g;
	int newW = (int)(originalImage.getWidth() * xScaleFactor);
	int newH = (int)(originalImage.getHeight() * yScaleFactor); 
	g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); 
	g2.drawImage(originalImage, 0, 0, newW, newH, null); } 
*/

//our image viewer
//todo: make sure it's clean enough to make multiple instances of (it isn't right now)
public class ezjiv {
	//info strings
	String version = "v0.7beta"; //major.minor, v0.10 is more than v0.9; this has changed from before -- versions without a v had 0.10 be the same as 0.1
	String titleText="[ezjiv by b.m.deeal] - "; //titlebar prefix
	//used to pick a random image
	Random rand = new Random();
	//used to select an image to view
	JFileChooser filePicker = new JFileChooser();
	//used to select a folder to view
	JFileChooser folderPicker = new JFileChooser();
	//this is the picture to view
	ImageIcon image=new ImageIcon();
	ImageIcon scaledImage=new ImageIcon();
	//this is the location of the picture to view
	URL fileName;
	//this is where the item is going into, gets a simple default message
	JLabel label = new JLabel("Welcome to ezjiv! No image loaded.");
	//this is the view area (todo: might need to subclass this?)
	JScrollPane scroll=new JScrollPane(label,ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
	//this is the window itself
	JFrame f;
	//this holds the list of images in the folder
	FileInfo[] dir;
	FileInfo current; //current file that's open
	int dirIndex=0;
	//supported formats
	FileNameExtensionFilter filter = new FileNameExtensionFilter("Images", FileInfo.formats);
	//size of the image in percent (100 is full size, 50 half, 25 quarter, 12.5 eighth, etc)
	double scale=100;

	//get the scale required to fit an image to the screen
	public double getFitScale() {
		double scaleX, scaleY;
		Dimension d = scroll.getViewport().getExtentSize();
		scaleX = (double)d.width/(double)image.getIconWidth()*100.0;
		scaleY = (double)d.height/(double)image.getIconHeight()*100.0;
		return Math.min(scaleX,scaleY);
	}

	//show our nice title on the titlebar
	public void updateTitle() {
		f.setTitle(titleText+dir[dirIndex].fullName);
	}
	
	//something bad happened, complain loudly even though you can't do anything about it
	public void displayException(Exception e) {
		JOptionPane.showMessageDialog(null, e.toString(), "Error...", JOptionPane.ERROR_MESSAGE);
		e.printStackTrace();
	}
	
	//When re-sorting, we call this to stay on the current image.
	//this feels like it should be slow, but honestly, I've done it on a folder with upwards of 10k images without any real trouble
	public void seekToCurrent() {
		for (int ii=0; ii<dir.length; ii++) {
			if (current==dir[ii]) {
				dirIndex=ii;
				return;
			}
		}
	}
	
	//Sort the directory list by name:
	public void sortByName() {
		if (dir != null && dir.length > 1) {
			Arrays.sort(dir,(FileInfo a, FileInfo b)->a.compareName(b));
			seekToCurrent();
		}
	}
	
	//Sort the directory list by size:
	public void sortBySize() {
		if (dir != null && dir.length > 1) {
			Arrays.sort(dir,(FileInfo a, FileInfo b)->a.compareSize(b));
			seekToCurrent();
		}
	}
	
	//Sort the directory list by type:
	public void sortByType() {
		if (dir != null && dir.length > 1) {
			Arrays.sort(dir,(FileInfo a, FileInfo b)->a.compareExtension(b));
			seekToCurrent();
		}
	}
	
	//Sort the directory list by date:
	public void sortByDate() {
		if (dir != null && dir.length > 1) {
			Arrays.sort(dir,(FileInfo a, FileInfo b)->a.compareTimestamp(b));
			seekToCurrent();
		}
	}

	//Shuffle the directory list at random.
	//kinda feels weird that Java doesn't have a generic function that does this to any plain array
	public void sortRandomly() {
		if (dir != null && dir.length > 1) {
			for (int ii=0; ii<dir.length-2; ii++) {
				int n=ii+rand.nextInt(dir.length-ii);
				FileInfo tmp = dir[n];
				dir[n]=dir[ii];
				dir[ii]=tmp;
			}
			seekToCurrent();
		}
	}
	
	//Zoom the image on screen. Everything about this is awful.
	//todo: absolute rewrite -- it really shouldn't scale the WHOLE image at once, just what's being drawn
	public void zoomImage() {
		if (image.getImage() != null) {
			if (scaledImage.getImage() != null) {
				scaledImage.getImage().flush();
			}
			//terrible, dirty hack that tries to prevent Java from running out of memory if you zoom too far
			double tsx, tsy, tsf;
			final double MAX_WIDTH=6000;
			final double MAX_HEIGHT=6000;
			tsx=(image.getIconWidth()*scale/100.0);
			tsy=(image.getIconHeight()*scale/100.0);
			tsf=tsx/tsy;
			if (tsx > MAX_WIDTH || tsy > MAX_HEIGHT) {
				tsx=MAX_WIDTH*tsf;
				tsy=MAX_HEIGHT*tsf;
			}
			
			//dirty hack that lets .gif files play still since I haven't written my own decoder
			if (current.extension.toLowerCase().equals("gif")) {
				scaledImage = new ImageIcon(image.getImage().getScaledInstance((int)tsx,(int)tsy, Image.SCALE_DEFAULT));
			}
			else {
				scaledImage = new ImageIcon(getScaledInstanceSmooth(image.getImage(),(int)tsx, (int)tsy));
			}
		}
	}
	
	//Get a smooth scaled version of an image.
	//todo: this is a quick and dirty hack, scaling should be done on-the-fly on small regions of the image and not all at once in memory
	public Image getScaledInstanceSmooth(Image i, int w, int h) {
		/*BufferedImage result = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
		AffineTransform transform = new AffineTransform();
		double ww = i.getWidth();
		double hh = i.getHeight();
		transform.scale((double)w/ww,(double)h/hh);
		AffineTransformOp op = new AffineTransformOp(transform,AffineTransformOp.BILINEAR);
		scaleOp.filter(i, result);*/
		
		BufferedImage result = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D gfx = result.createGraphics();
		gfx.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		gfx.drawImage(i,0,0,w,h, null);
		return result;
	}

	//ask for a folder to open
	public void askForFolder() {
		folderPicker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		switch (folderPicker.showOpenDialog(null)) {
			case JFileChooser.APPROVE_OPTION:
				try {
					FileInfo[] oldDir=dir;
					FileInfo selectedFolder=new FileInfo(folderPicker.getSelectedFile());
					dir=FileInfo.toFileInfo(selectedFolder.file.listFiles((File f)->FileInfo.accept(f)));
					if (dir.length==0) {
						dir=oldDir;
						JOptionPane.showMessageDialog(null, "There are no images in this folder.", "No images found!",JOptionPane.WARNING_MESSAGE);
					}
					else {
						dirIndex=0;
						switchToImage();
					}
				}
				catch (Exception e) {
					//dir=oldDir; maybe? dunno
					displayException(e);
				}
			break;
			default:
				return;
		}
	}

	//ask for an image to open
	//todo: probably could refactor this
	public void askForImage() {
		filePicker.setFileFilter(filter);
		switch (filePicker.showOpenDialog(null)) {
			case JFileChooser.APPROVE_OPTION:
				try {
					//ask for image
					FileInfo selectedFile=new FileInfo(filePicker.getSelectedFile());
					current=selectedFile; //doing this fixes a bug in the scaling function's format detection
					fileName=selectedFile.file.toURI().toURL();
					//load, scale, and show image
					image=new ImageIcon(fileName); 
					scale=getFitScale();
					showNewImage();
					//get where in the (arbitrarily sorted) image list we are -- we don't sort at all by default
					//I think I can replace this bit with a function I wrote already? really should check and refactor if so
					dir=FileInfo.toFileInfo(selectedFile.file.getParentFile().listFiles((File f)->FileInfo.accept(f)));
					dirIndex=0;
					for (int ii=0; ii<dir.length; ii++) {
						if (dir[ii].fullName.equals(selectedFile.fullName)) {
							dirIndex=ii;
							current=dir[ii];
						}
					}
					updateTitle();
				}
				catch (Exception e) { //todo: real error handling, and I should pick up on any weird stuff from the FileInfo object
					displayException(e);
				}
			break;
			default:
				return;
		}
	}

	//Jump to the image at the current index in the directory list.
	public void switchToImage() {
		try {
			fileName=dir[dirIndex].file.toURI().toURL();
			if (image.getImage() != null) {
				image.getImage().flush(); //had memory use go crazy from not doing this
			}
			current=dir[dirIndex];
			image=new ImageIcon(fileName);
			scale=getFitScale(); //todo: fit to window?
			showNewImage();
			updateTitle();
		}
		catch (Exception e) {
			displayException(e);
		}
	}

	//Set the scale of an image and show the rescaled image:
	public void rescaleImage(double s) {
		if (image.getImage() != null) {
			scale=s;
			showNewImage();
		}
	}
	
	//Go to the first image in the directory list:
	public void firstImage() {
		if (dir != null) {
			dirIndex=0;
			switchToImage();
		}
	}
	
	//Go to the last image in the directory list:
	public void lastImage() {
		if (dir != null) {
			dirIndex=dir.length-1;
			switchToImage();
		}
	}

	//Go to the next image in the directory list:
	public void nextImage() {
		if (dir != null) {
			dirIndex++;
			if (dirIndex>=dir.length) {
				dirIndex=0;
			}
			switchToImage();
		}
	}

	//Go to the previous image in the directory list:
	public void previousImage() {
		if (dir != null) {
			dirIndex--;
			if (dirIndex<0) {
				dirIndex=dir.length-1;
			}
			switchToImage();
		}
	}

	//Go to a random image in the directory list:
	//todo: make it not pick the same image twice, or at the very least make it less likely
	public void randomImage() {
		if (dir != null) {
			dirIndex=rand.nextInt(dir.length);
			switchToImage();
		}
	}

	//View the image, repaint everything, handle scrollbars, etc.
	//This gets called a lot.
	public void showNewImage() {
		//clean up the 
		label.setText("");
		zoomImage();
		//put the image onto a component
		label.setIcon(scaledImage);
		//recreate the scrolling area
		//it feels like there should be a better way to do this and I'm probably just doing this wrong
		f.remove(scroll);
		scroll = new JScrollPane(label,ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		//todo: make this configurable
		scroll.getHorizontalScrollBar().setUnitIncrement(8);
		scroll.getVerticalScrollBar().setUnitIncrement(8);
		//make sure everything draws properly, and we run the gc to keep memory use low on image switch
		f.add(scroll, BorderLayout.CENTER);
		f.invalidate();
		f.revalidate();
		f.repaint();
		System.gc();
	}

	//show credits! (just me)
	public void aboutMessage() {
		JOptionPane.showMessageDialog(null, "ezjiv -- a simple Java image viewer\n\u00a9 2019 B.M.Deeal.\nLicensed under the GNU GPL, versions 2.0 and 3.0.\n\nThis is version "+version+".","About",JOptionPane.INFORMATION_MESSAGE);
	}

	//show a window
	//don't actually call this from another class yet, there's totally a System.exit somewhere here
	public void runGUI() {
		//window styling, size
		f = new JFrame(titleText+"no file loaded");
		Dimension screenSize;
		screenSize=Toolkit.getDefaultToolkit().getScreenSize();
		int myWidth, myHeight;
		myWidth=screenSize.width;
		myHeight=screenSize.height;
		f.setSize(myWidth*5/6,myHeight*5/6); //take up most of, but not all of the screen
		//f.setLayout(new BorderLayout());
		f.setLocationRelativeTo(null); //center on screen
		f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		f.setLayout(new BorderLayout());
		label.setForeground(new Color(160,160,160));
		label.setBackground(new Color(16,16,16)); //pleasing, not distracting off-black
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setVerticalAlignment(SwingConstants.CENTER);
		label.setOpaque(true);
		//menu bar stuff
		JMenuBar menuBar = new JMenuBar();
		//file menu
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);
		//open image
		JMenuItem openItem = new JMenuItem("Open File");
		openItem.setMnemonic(KeyEvent.VK_O);
		openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
		openItem.addActionListener((ActionEvent e)->askForImage());
		fileMenu.add(openItem);
		//open folder
		JMenuItem openFolderItem = new JMenuItem("Open Folder");
		openFolderItem.setMnemonic(KeyEvent.VK_F);
		openFolderItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK+ActionEvent.SHIFT_MASK));
		openFolderItem.addActionListener((ActionEvent e)->askForFolder());
		fileMenu.add(openFolderItem);
		//exit program
		JMenuItem exitItem = new JMenuItem("Exit");
		exitItem.setMnemonic(KeyEvent.VK_X);
		exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, ActionEvent.CTRL_MASK));
		exitItem.addActionListener((ActionEvent e)->System.exit(0)); //todo: ask to exit maybe? or at the very least, don't just do System.exit immediately
		fileMenu.add(exitItem);
		//end of file menu
		menuBar.add(fileMenu);
		//navigation menu
		JMenu navMenu = new JMenu("Navigation");
		navMenu.setMnemonic(KeyEvent.VK_N);
		//sort menu
		JMenu sortMenu = new JMenu("Sort...");
		//by name
		JMenuItem byNameItem = new JMenuItem("by Name");
		byNameItem.addActionListener((ActionEvent e)->sortByName());
		byNameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,0));
		sortMenu.add(byNameItem);
		//by date
		JMenuItem byDateItem = new JMenuItem("by Date");
		byDateItem.addActionListener((ActionEvent e)->sortByDate());
		byDateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,0));
		sortMenu.add(byDateItem);
		//by size
		JMenuItem bySizeItem = new JMenuItem("by Size");
		bySizeItem.addActionListener((ActionEvent e)->sortBySize());
		bySizeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,0));
		sortMenu.add(bySizeItem);
		//at random
		JMenuItem randomlyItem = new JMenuItem("Randomly");
		randomlyItem.addActionListener((ActionEvent e)->sortRandomly());
		randomlyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R,0));
		sortMenu.add(randomlyItem);
		//end of sort menu
		navMenu.add(sortMenu);
		//first image
		JMenuItem firstItem = new JMenuItem("First Image");
		firstItem.setMnemonic(KeyEvent.VK_F);
		firstItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_HOME,0));
		firstItem.addActionListener((ActionEvent e)->firstImage());
		navMenu.add(firstItem);
		//last image
		JMenuItem lastItem = new JMenuItem("Last Image");
		lastItem.setMnemonic(KeyEvent.VK_L);
		lastItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_END,0));
		lastItem.addActionListener((ActionEvent e)->lastImage());
		navMenu.add(lastItem);
		//previous image
		JMenuItem previousItem = new JMenuItem("Previous Image");
		previousItem.setMnemonic(KeyEvent.VK_P);
		previousItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,0));
		previousItem.addActionListener((ActionEvent e)->previousImage());
		navMenu.add(previousItem);
		//next image
		JMenuItem nextItem = new JMenuItem("Next Image");
		nextItem.setMnemonic(KeyEvent.VK_N);
		nextItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,0));
		nextItem.addActionListener((ActionEvent e)->nextImage());
		navMenu.add(nextItem);
		//random image
		JMenuItem randomItem = new JMenuItem("Random Image");
		randomItem.setMnemonic(KeyEvent.VK_R);
		randomItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z,0));
		randomItem.addActionListener((ActionEvent e)->randomImage());
		navMenu.add(randomItem);
		//end of navigation menu
		menuBar.add(navMenu);
		//view menu
		JMenu viewMenu = new JMenu("View");
		viewMenu.setMnemonic(KeyEvent.VK_V);
		//fit to screen
		JMenuItem fitZoomItem = new JMenuItem("Fit to Screen");
		fitZoomItem.setMnemonic(KeyEvent.VK_R);
		fitZoomItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,0));
		fitZoomItem.addActionListener((ActionEvent e)->rescaleImage(getFitScale()));
		viewMenu.add(fitZoomItem);
		//zoom in
		JMenuItem zoomInItem = new JMenuItem("Zoom In (2x)");
		zoomInItem.setMnemonic(KeyEvent.VK_I);
		zoomInItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP,0));
		zoomInItem.addActionListener((ActionEvent e)->rescaleImage(scale*2));
		viewMenu.add(zoomInItem);
		//zoom out (todo: fine-tuned options)
		JMenuItem zoomOutItem = new JMenuItem("Zoom Out (2x)");
		zoomOutItem.setMnemonic(KeyEvent.VK_O);
		zoomOutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,0));
		zoomOutItem.addActionListener((ActionEvent e)->rescaleImage(scale/2));
		viewMenu.add(zoomOutItem);
		//reset zoom
		JMenuItem resetZoomItem = new JMenuItem("Reset Zoom (100%)");
		resetZoomItem.setMnemonic(KeyEvent.VK_R);
		resetZoomItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0));
		resetZoomItem.addActionListener((ActionEvent e)->rescaleImage(100));
		viewMenu.add(resetZoomItem);
		//end of view menu
		menuBar.add(viewMenu);
		//help menu
		JMenu helpMenu = new JMenu("Help");
		helpMenu.setMnemonic(KeyEvent.VK_H);
		//about this program
		JMenuItem aboutItem = new JMenuItem("About");
		aboutItem.setMnemonic(KeyEvent.VK_A);
		aboutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1,0));
		aboutItem.addActionListener((ActionEvent e)->aboutMessage());
		helpMenu.add(aboutItem);
		//end of help menu
		menuBar.add(helpMenu);
		//lay everything out
		f.add(menuBar, BorderLayout.PAGE_START);
		f.add(scroll, BorderLayout.CENTER);
		f.validate();
		//show everything
		f.setVisible(true);
	}

	//run the program
	//ideally, you could embed ezjiv in your own program, but as of right now, System.exit gets called
	public static void main(String[] args) {
		//todo: user-controlled theme options
		try {
			MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
			UIManager.setLookAndFeel(new MetalLookAndFeel());
		}
		catch (Exception e) { //not being able to set the theme doesn't matter one bit, although I should put a far less broad exception check here (eg, headless exceptions?)
			System.out.println(e);
		}
		ezjiv prog = new ezjiv();
		prog.runGUI();
	}
}
