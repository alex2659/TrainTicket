package q6.trainticket;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Environment;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class ImageProcess {

	private Bitmap bufImage;
    private int height=60, width=200;   //預設圖片高度、寬度

    public static ArrayList<Bitmap> mainProcedure(Bitmap inputBitmap) throws InterruptedException
    {
        double thinnestRatio;
        Bitmap thinnestImage;
        Bitmap img;
        Bitmap dstImg;
        ArrayList<Bitmap> imageList;
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath();

        if( inputBitmap==null )
            img = BitmapFactory.decodeFile( path + "/mypic50.png");
        else
            img = inputBitmap;

        ImageProcess imgprs = new ImageProcess( img );
//        imgprs.saveImage("P1_captcha.png");

        imgprs.deepenBackground();
//    	imgprs.saveImage("P2_deepenBackground.png");

        imgprs.fillHorizontalWhiteLines();
        imgprs.fillVerticalWhiteLines();
//    	imgprs.saveImage("P3_fillWhiteLine.png");

        imgprs.eraseBackground();
//    	imgprs.saveImage("P4_eraseBackground.png");

        imgprs.eraseGrayLines();
//    	imgprs.saveImage("P5_0_eraseLines.png");

        imgprs.levelize(128);
//    	imgprs.saveImage("P5_1_levelize.png");

        imgprs.fillSmallHolesInGrayScale();
        imgprs.fillSmallHolesInGrayScale();
//    	imgprs.saveImage("P5_2_fillSmallHoles.png");
//
//    	imgprs.labelWord();
//    	imgprs.saveImage("P5_2_labelWord.png");
//
        imgprs.enhanceDigitThenRemoveNoise();
//    	imgprs.saveImage("P6_eraseNoise.png");
//
        imgprs.fillSmallHolesInRGB();
//        imgprs.saveImage("P7_fillSmallHoles.png");
//
        imageList = imgprs.partitionCAPTCHAwithBFS();

        for(int a=0; a<6; a++)
        {
            img = imageList.get(a);
            img = enlargePictureThreeTimes(img);    //expand the width 3 times to prevent from cropping loss after rotation.

            thinnestRatio = 10000;
            thinnestImage = null;

            //used for 60, -60 degrees
            for(int b=-60; b<=60; b+=15)
            {
                dstImg = Bitmap.createBitmap(img.getWidth(), img.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas mycanvas = new Canvas(dstImg);
                Matrix matrix = new Matrix();
                matrix.setRotate(b, img.getWidth()/2, img.getHeight()/2);
                mycanvas.drawBitmap(img, matrix, null);

				/* Eliminate black background during rotation. */
                makeBackgroundWhiteAroundCorner(dstImg, 0,0,1);
                makeBackgroundWhiteAroundCorner(dstImg, 0,dstImg.getHeight()-1,3);
                makeBackgroundWhiteAroundCorner(dstImg, dstImg.getWidth()-1,0,2);
                makeBackgroundWhiteAroundCorner(dstImg, dstImg.getWidth()-1, dstImg.getHeight()-1,4);

                dstImg = cropImage(dstImg);
                if( (double)dstImg.getWidth()/(double)dstImg.getHeight() < thinnestRatio )
                {
                    thinnestRatio = (double)dstImg.getWidth()/(double)dstImg.getHeight();
                    thinnestImage = dstImg;
                }
            }

            //record the thinnest
            try {
//                OutputStream stream = new FileOutputStream( path + "/" + a + "st.png");
//                OutputStream stream = new FileOutputStream( "/storage/emulated/0/Pictures/" + a + "st.png");
                if( thinnestImage!=null ) {
//					thinnestImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
					imageList.set(a,thinnestImage);
					//here we reuse imageList to save answer
					//be careful of not overwriting the original image!
				}
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

		return imageList;
    }

    public static void main(String args[]) throws InterruptedException
    {
        mainProcedure(null);
    }

    public void fillSmallHolesInGrayScale(){
    	ArrayList<Point> myList = new ArrayList<>();
    	for(int x=1; x<width-1; x++)
    		for(int y=1; y<height-1; y++)
    		{
    			if( !pointIsVeryGray(x,y)		//center is white
    					&&
    					(
    							(pointIsVeryGray(x-1,y)&&pointIsVeryGray(x+1,y))	//up and down are black
    							||
    							(pointIsVeryGray(x,y-1)&&pointIsVeryGray(x,y+1)) //left and right are black
    					)
    				)
    			{
    				myList.add(new Point(x,y));
    			}
    		}
    	for( Point temp : myList )
    		bufImage.setPixel(temp.x, temp.y, 0xFF000000);
    }

    public void fillSmallHolesInRGB(){
    	ArrayList<Point> myList = new ArrayList<>();
    	for(int x=1; x<width-1; x++)
    		for(int y=1; y<height-1; y++)
    		{
    			if( pointIsWhite(x,y)		//center is white
    					&&
    					(
    							(pointIsBlack(x-1,y)&&pointIsBlack(x+1,y))	//up and down are black
    							||
    							(pointIsBlack(x,y-1)&&pointIsBlack(x,y+1)) //left and right are black
    					)
    				)
    			{
    				myList.add(new Point(x,y));
    			}
    		}
    	for( Point temp : myList )
    		bufImage.setPixel(temp.x, temp.y, 0xFF000000);
    }

    public boolean pointIsVeryGray(int i, int j)
    {
		return (bufImage.getPixel(i, j)&0xFF) < 110;
    }

    public boolean isInBound(int x, int y)
    {
    	return 0<=x && x<width && 0<=y && y<height;
    }

    public void enhanceDigitThenRemoveNoise(){
    	boolean traversed [][] = new boolean[width][height];
    	Queue<Point> myQueue = new LinkedList<>();
    	ArrayList<Point> localList = new ArrayList<>();
    	ArrayList<Point> globalList = new ArrayList<>();
    	for(int i=0; i<width; i++)
    		for(int j=0; j<height; j++)
    		{
    			if( !traversed[i][j] && pointIsVeryGray(i,j) )
    			{
					myQueue.add(new Point(i,j));
					while( !myQueue.isEmpty() )
					{
						Point temp = myQueue.remove();
						if( !traversed[temp.x][temp.y] && pointIsVeryGray(temp.x,temp.y) )
						{
							localList.add(temp);
	    					traversed[temp.x][temp.y] = true;
	    					for(int p=-1; p<=1; p++)
	    						for(int q=-1; q<=1; q++)
	    							if( isInBound(temp.x+p, temp.y+q) && !traversed[temp.x+p][temp.y+q] && pointIsVeryGray(temp.x+p,temp.y+q) )
	    								myQueue.add(new Point(temp.x+p,temp.y+q));
						}
					}
    			}
				if( localList.size()>=25)//15 )
					globalList.addAll(localList);
				localList.clear();
    		}
    	for(int i=0; i<width; i++)
    		for(int j=0; j<height; j++)
    			bufImage.setPixel(i, j, 0xFFFFFFFF);
    	for (Point temp : globalList)
    		bufImage.setPixel(temp.x, temp.y, 0xFF000000);
    }

    public void levelize(int levelSize){
    	for(int i=0; i<bufImage.getWidth(); i++)
    		for(int j=0; j<bufImage.getHeight(); j++)
    		{
    			int color = bufImage.getPixel(i, j);
    			int red = Color.red(color)/levelSize*levelSize;
    			int green = Color.green(color)/levelSize*levelSize;
    			int blue = Color.blue(color)/levelSize*levelSize;
    			bufImage.setPixel(i, j, 0xFF000000 | red<<16 | green<<8 | blue);
    		}
    	bufImage = convertToGrayScale(bufImage);
    }

    public static Bitmap convertToGrayScale(Bitmap image) {

        Bitmap bmpGrayscale = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(image, 0, 0, paint);
        return bmpGrayscale;
    }

	public ImageProcess( Bitmap in ) {
        bufImage = in;
        bufImage = bufImage.copy(Bitmap.Config.ARGB_8888 ,true);
        height = bufImage.getHeight();
        width = bufImage.getWidth();
	}

	public static Bitmap enlargePictureThreeTimes(Bitmap input)
	{
		Bitmap output = Bitmap.createBitmap(input.getWidth()*3, input.getHeight()*3, Bitmap.Config.ARGB_8888);
		for(int i=0; i<output.getWidth(); i++)
			for(int j=0; j<output.getHeight(); j++)
			{
				if( output.getWidth()/3<=i && i<output.getWidth()*2/3 && output.getHeight()/3<=j && j<output.getHeight()*2/3 )
					output.setPixel(i, j, input.getPixel(i-output.getWidth()/3, j-output.getHeight()/3));
				else
					output.setPixel(i,j,0xFFFFFFFF);
			}
		return output;
	}

	public void deepenBackground(){
		int backColor;
		int threshold = 30;
		int targetColor = 0xFFC8C8C8;
		if( colorDist(bufImage.getPixel(0, 0), 0)>10 )
			backColor = bufImage.getPixel(0,0);
		else	//if point(0,0) is noise, choose point(0,5) as background.
            backColor = bufImage.getPixel(0,5);
		for(int i=0; i<bufImage.getWidth(); i++)
			for(int j=0; j<bufImage.getHeight(); j++){
				int nowColor = bufImage.getPixel(i, j);
				if( Math.abs(Color.red(nowColor)-Color.red(backColor))<=threshold &&
					 Math.abs(Color.green(nowColor)-Color.green(backColor))<=threshold &&
					 Math.abs(Color.blue(nowColor)-Color.blue(backColor))<=threshold ){
					bufImage.setPixel(i, j, targetColor);
				}
			}
		bufImage.setPixel(0, 0, targetColor);
	}

	public void eraseBackground(){
		int backColor;
		int threshold = 30;
		if( colorDist(bufImage.getPixel(0, 0), 0)>10 )
            backColor = bufImage.getPixel(0,0);
		else	//if point(0,0) is noise, choose point(0,5) as background.
            backColor = bufImage.getPixel(0,5);
		for(int i=0; i<bufImage.getWidth(); i++)
			for(int j=0; j<bufImage.getHeight(); j++){
				int nowColor = bufImage.getPixel(i, j);
                if( Math.abs(Color.red(nowColor)-Color.red(backColor))<=threshold &&
                        Math.abs(Color.green(nowColor)-Color.green(backColor))<=threshold &&
                        Math.abs(Color.blue(nowColor)-Color.blue(backColor))<=threshold ){
					bufImage.setPixel(i, j, 0xFFFFFFFF);
				}
			}
		bufImage.setPixel(0, 0, 0xFFFFFFFF);
	}

	public void saveImage(String filename){
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath();
        try {
            OutputStream stream = new FileOutputStream(path + "/" +filename);
            bufImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void makeBackgroundWhiteAroundCorner(Bitmap dstImg, int x, int y, int dir){
		if( dir==1 )
		{
			for(int i=0; i<=1; i++)
				for(int j=0; j<=1; j++)
					if( 0<=x+i && x+i<dstImg.getWidth() && 0<=y+j && y+j<dstImg.getHeight() && (dstImg.getPixel(x+i,y+j)&0xFFFFFF)<0x000F0F0F )
                    {
						dstImg.setPixel(x+i, y+j, 0xFFFFFFFF);
						makeBackgroundWhiteAroundCorner(dstImg, x+i, y+j, dir);
					}
		}
		else if( dir==2 )
		{
			for(int i=-1; i<=0; i++)
				for(int j=0; j<=1; j++)
					if( 0<=x+i && x+i<dstImg.getWidth() && 0<=y+j && y+j<dstImg.getHeight() && (dstImg.getPixel(x+i,y+j)&0xFFFFFF)<0x000F0F0F )
					{
						dstImg.setPixel(x+i, y+j, 0xFFFFFFFF);
						makeBackgroundWhiteAroundCorner(dstImg, x+i, y+j, dir);
					}
		}
		else if( dir==3 )
		{
			for(int i=0; i<=1; i++)
				for(int j=-1; j<=0; j++)
					if( 0<=x+i && x+i<dstImg.getWidth() && 0<=y+j && y+j<dstImg.getHeight() && (dstImg.getPixel(x+i,y+j)&0xFFFFFF)<0x000F0F0F )
					{
						dstImg.setPixel(x+i, y+j, 0xFFFFFFFF);
						makeBackgroundWhiteAroundCorner(dstImg, x+i, y+j, dir);
					}
		}
		else
		{
			for(int i=-1; i<=0; i++)
				for(int j=-1; j<=0; j++)
					if( 0<=x+i && x+i<dstImg.getWidth() && 0<=y+j && y+j<dstImg.getHeight() && (dstImg.getPixel(x+i,y+j)&0xFFFFFF)<0x000F0F0F )
					{
						dstImg.setPixel(x+i, y+j, 0xFFFFFFFF);
						makeBackgroundWhiteAroundCorner(dstImg, x+i, y+j, dir);
					}
		}
	}

	public boolean pointIsWhite(int x, int y)
	{
		int c = bufImage.getPixel(x, y);
		return ( 255-Color.red(c)<=10 && 255-Color.green(c)<=10 && 255-Color.blue(c)<=10 );
	}

	public boolean pointIsBlack(int x, int y)
	{
        int c = bufImage.getPixel(x, y);
		return ( Color.red(c)<=10 && Color.green(c)<=10 && Color.blue(c)<=10 );
	}

	public static double colorDist(int x, int y)	// input with (A)RGB mode
	{
		return Math.sqrt( Math.pow(Color.red(x)-Color.red(y), 2) + Math.pow(Color.green(x)-Color.green(y), 2) + Math.pow(Color.blue(x)-Color.blue(y), 2) );
	}

	private int findGrayLineColor(){
		int reds=0, greens=0, blues=0, numOfPoints=0;
		boolean status = false;
		for(int i=1; i<width-1; i++)
		{
			for(int j=height-1; j>=0; j--)
			{
				if( !pointIsWhite(i,j) && pointIsWhite(i-1,j) && pointIsWhite(i+1,j) )
				{
					status = true;	//continue to extend line
					reds += ((bufImage.getPixel(i, j)&0x00FF0000) >> 16);
					greens += ((bufImage.getPixel(i, j)&0x0000FF00) >> 8);
					blues += ((bufImage.getPixel(i, j)&0x000000FF) );
					numOfPoints++;
				}
				else if( status )
				{
					if( numOfPoints < 5 )	//if this line is too short, continue to find
					{
						status = false;
						reds = 0;
						greens = 0;
						blues = 0;
						numOfPoints = 0;
					}
					else	//otherwise, stop finding
						break;
				}
			}
			if( status )	//stop finding
				break;
		}
		return 0xFF000000 | (reds/numOfPoints)<<16 |  (greens/numOfPoints)<<8 |  (blues/numOfPoints);
	}

	public void eraseGrayLines() {
        ArrayList<Point> myList = new ArrayList<>();
        int lineColor = findGrayLineColor();
        int range = 20; //20, 36, 240
        for (int i = 0; i < bufImage.getWidth(); i++)
            for (int j = 0; j < bufImage.getHeight(); j++)
                if (colorDist(bufImage.getPixel(i, j), lineColor) < range) {
                    int counter = 0;
                    for (int p = -1; p <= 1; p++)
                        for (int q = -1; q <= 1; q++)
                            if (0 <= i + p && i + p < width && 0 <= j + q && j + q < height && !pointIsWhite(i + p, j + q) && colorDist(bufImage.getPixel(i + p, j + q), lineColor) >= range)
                                counter++;
                    if (counter <= 3)
                        myList.add(new Point(i, j));
                }
        for (Point temp : myList)
            bufImage.setPixel(temp.x, temp.y, 0xFFFFFFFF);
    }

	public void fillHorizontalWhiteLines(){
		int whiteLineColor = 0xFFFFFFFF;
		ArrayList<Point> pointList = new ArrayList<>();
		Map<Point, Integer> colorMap = new HashMap<>(); // record the color to be filled in that point
		for(int i=0; i<bufImage.getWidth(); i++)
			for(int j=0; j<bufImage.getHeight(); j++)
				if( colorDist(bufImage.getPixel(i, j),whiteLineColor)<50 )
				{
					int counter=0;
					for(int p=-1; p<=1; p++)
						for(int q=-1; q<=1; q++)
							if( 0<=i+p && i+p<width && 0<=j+q && j+q<height && colorDist(bufImage.getPixel(i+p, j+q),whiteLineColor)<50 )//pointIsWhite(i+p,j+q) )
								counter++;
					if( counter<=10 )
					{
						if( i>=1 && i<width-1 )
						{
							pointList.add(new Point(i,j));
							if( colorDist(bufImage.getPixel(i+1, j), 0) <= colorDist(bufImage.getPixel(i-1, j), 0) )
								colorMap.put( pointList.get(pointList.size()-1), bufImage.getPixel(i+1, j) );
							else
								colorMap.put( pointList.get(pointList.size()-1), bufImage.getPixel(i-1, j) );
						}
					}
				}
		for (Point temp : pointList)
			bufImage.setPixel(temp.x, temp.y, colorMap.get(temp));
	}

	public void fillVerticalWhiteLines() {
        ArrayList<Point> pointList = new ArrayList<>();
        Map<Point, Integer> colorMap = new HashMap<>(); // record the color to be filled in that point
        int whiteLineColor = 0xFFFFFFFF;
        for (int i = 0; i < bufImage.getWidth(); i++)
            for (int j = 0; j < bufImage.getHeight(); j++)
                if (colorDist(bufImage.getPixel(i, j), whiteLineColor) < 50) {
                    int counter = 0;
                    for (int p = -1; p <= 1; p++)
                        for (int q = -1; q <= 1; q++)
                            if (0 <= i + p && i + p < width && 0 <= j + q && j + q < height && colorDist(bufImage.getPixel(i + p, j + q), whiteLineColor) < 50)//pointIsWhite(i+p,j+q) )
                                counter++;
                    if (counter <= 10) {
                        if (j >= 1 && j < height - 1) {
                            pointList.add(new Point(i, j));
                            if (colorDist(bufImage.getPixel(i, j + 1), 0) <= colorDist(bufImage.getPixel(i, j - 1), 0))
                                colorMap.put(pointList.get(pointList.size() - 1), bufImage.getPixel(i, j + 1));
                            else
                                colorMap.put(pointList.get(pointList.size() - 1), bufImage.getPixel(i, j - 1));
                        }
                    }
                }
        for (Point temp : pointList)
            bufImage.setPixel(temp.x, temp.y, colorMap.get(temp));
    }

	public ArrayList<Bitmap> partitionCAPTCHAwithBFS(){
		ArrayList<Bitmap> imageList = new ArrayList<>();
    	Queue<Point> myQueue = new LinkedList<>();
    	ArrayList<Point> localList = new ArrayList<>();
    	ArrayList<ArrayList<Point>> globalList = new ArrayList<>();
    	boolean traversed [][] = new boolean[width][height];
    	for(int i=0; i<width; i++)
    		for(int j=0; j<height; j++)
    		{
    			if( !traversed[i][j] && pointIsVeryGray(i,j) )
    			{
					myQueue.add(new Point(i,j));
					while( !myQueue.isEmpty() )
					{
						Point temp = myQueue.remove();
						if( !traversed[temp.x][temp.y] && pointIsVeryGray(temp.x,temp.y) )
						{
							localList.add(temp);
	    					traversed[temp.x][temp.y] = true;
	    					for(int p=-1; p<=1; p++)
	    						for(int q=-1; q<=1; q++)
	    							if( isInBound(temp.x+p, temp.y+q) && !traversed[temp.x+p][temp.y+q] && pointIsVeryGray(temp.x+p,temp.y+q) )
	    								myQueue.add(new Point(temp.x+p,temp.y+q));
						}
					}
    			}
				if( localList.size()>=25)//15 )
					globalList.add(new ArrayList<>(localList));
				localList.clear();
    		}

    	// sort global list to give larger area a higher priority
		Collections.sort(globalList, new Comparator<ArrayList<Point>>() {
			@Override
			public int compare(ArrayList<Point> a, ArrayList<Point> b)
			{
				return - (a.size()-b.size());
			}
		});
    	if( globalList.size()>=6 )
    		globalList = new ArrayList<>(globalList.subList(0, 6));
    	Collections.sort(globalList, new Comparator<ArrayList<Point>>() {
	        @Override
	        public int compare(ArrayList<Point> a, ArrayList<Point> b)
	        {
	            int leftMostA = width, leftMostB = width;
	            for( Point temp : a)
	            	if( temp.x < leftMostA )
	            		leftMostA = temp.x;
	            for( Point temp : b)
	            	if( temp.x < leftMostB )
	            		leftMostB = temp.x;
	            return leftMostA-leftMostB;
	        }
	    });

    	//start partitioning and save them into files.
    	for(int i=0; i<globalList.size(); i++)
    	{
    		ArrayList<Point> tempList = globalList.get(i);
    		if( tempList.size()<=0 )
    			continue;
    		int leftBound = width;
    		int rightBound = -1;
    		int upBound = height;
    		int lowBound = -1;
    		for( Point tempPoint : tempList )
    		{
    			if( tempPoint.x < leftBound )
    				leftBound = tempPoint.x;
    			if( tempPoint.x > rightBound )
    				rightBound = tempPoint.x;
    			if( tempPoint.y < upBound )
    				upBound = tempPoint.y;
    			if( tempPoint.y > lowBound )
    				lowBound = tempPoint.y;
    		}
    		Bitmap out = Bitmap.createBitmap(bufImage, leftBound, upBound, rightBound-leftBound+1, lowBound-upBound+1);
    		imageList.add(out);
    	}
    	while( imageList.size()<6 )	// if the number of digits is too small, generate an additional white picture to make image processor happy
		{
            Bitmap out = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
			for(int i=0; i<out.getWidth(); i++)
				for(int j=0; j<out.getHeight(); j++)
					out.setPixel(i, j, 0xFFFFFFFF);
			imageList.add(out);
		}
    	return imageList;
    }

	public static Bitmap cropImage(Bitmap img)
	{
		int xLeft=0, xRight=img.getWidth()-1;	//左界，右界
		int yUp=0, yDown=img.getHeight()-1;	//上界，下界
		int range = 255;

		//detect left bound
		for(int i=0; i<img.getWidth(); i++)
		{
			int blackCount = 0;
			for(int j=0; j<img.getHeight(); j++)
			{
				if( colorDist(img.getPixel(i, j), 0)<range )
					blackCount++;
			}
			if( blackCount>0 )
			{
				xLeft = i;
				break;
			}
		}

		//detect right bound
		for(int i=img.getWidth()-1; i>=0; i--)
		{
			int blackCount = 0;
			for(int j=0; j<img.getHeight(); j++)
			{
				if( colorDist(img.getPixel(i, j), 0)<range )
					blackCount++;
			}
			if( blackCount>0 )
			{
				xRight = i;
				break;
			}
		}

		//detect up bound
		for(int i=0; i<img.getHeight(); i++)
		{
			int blackCount = 0;
			for(int j=0; j<img.getWidth(); j++)
			{
				if( colorDist(img.getPixel(j, i), 0)<range )
					blackCount++;
			}
			if( blackCount>0 )
			{
				yUp = i;
				break;
			}
		}

		//detect low bound
		for(int i=img.getHeight()-1; i>=0; i--)
		{
			int blackCount = 0;
			for(int j=0; j<img.getWidth(); j++)
			{
				if( colorDist(img.getPixel(j, i), 0)<range )
					blackCount++;
			}
			if( blackCount>0 )
			{
				yDown = i;
				break;
			}
		}

        return Bitmap.createBitmap(img, xLeft, yUp, xRight-xLeft+1, yDown-yUp+1);
	}

	public static int[] getPixelData(Bitmap inputBitmap) {
		if (inputBitmap == null) {
			return null;
		}

		int width = inputBitmap.getWidth();
		int height = inputBitmap.getHeight();

		// Get 28x28 pixel data from bitmap
		int[] pixels = new int[width * height];
		inputBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

		int[] retPixels = new int[pixels.length];
		for (int i = 0; i < pixels.length; ++i) {
			// Set 0 for white and 255 for black pixel
			int pix = pixels[i];
			int b = pix & 0xff;
			retPixels[i] = 0xff - b;
		}
		return retPixels;
	}

    public static Bitmap fillBlankTo28By28(Bitmap input){
        Bitmap output = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888);
		int inputWidth = (input.getWidth()%2==0) ? input.getWidth() : (input.getWidth()-1);
        int inputHeight = (input.getHeight()%2==0) ? input.getHeight() : (input.getHeight()-1);
        for(int i=0; i<output.getWidth(); i++)
            for(int j=0; j<output.getHeight(); j++)
            {
                if( (28-inputWidth)/2<=i && i<28-(28-inputWidth)/2
                        && (28-inputHeight)/2<=j && j<28-(28-inputHeight)/2 )
                    output.setPixel(i, j, input.getPixel(i-(28-inputWidth)/2, j-((28-inputHeight)/2)));
                else
                    output.setPixel(i,j,0xFFFFFFFF);
            }
		return output;
    }
}
