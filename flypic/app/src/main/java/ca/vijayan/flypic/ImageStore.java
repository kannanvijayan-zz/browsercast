package ca.vijayan.flypic;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Created by kannanvijayan on 2015-03-02.
 */
public class ImageStore {
    private File mPicDir;
    private List<String> mImageNames;
    private Map<String, Integer> mImageNameMap;
    private int mCurrentImage;

    public ImageStore() {
        mPicDir = getPicDir();
        mImageNames = listAllPictureNames();
        for (String picName : mImageNames) {
            Log.d("PicView.ImageStore", "Picture found: " + picName);
        }
        mImageNameMap = new TreeMap<String, Integer>();
        for (int i = 0; i < mImageNames.size(); i++) {
            mImageNameMap.put(mImageNames.get(i), new Integer(i));
        }
        mCurrentImage = 0;
    }

    public int numImages() {
        return mImageNames.size();
    }

    public int currentImage() {
        return mCurrentImage;
    }
    public int imageNumber(String name) {
        return ((Integer) mImageNameMap.get(name)).intValue();
    }

    public List<String> imageNames() {
        return mImageNames;
    }

    public File imageFile(int i) {
        assert(i >= 0 && i < mImageNames.size());
        return new File(mPicDir, mImageNames.get(i));
    }

    public Bitmap imageBitmap(int i) {
        File f = imageFile(i);
        try {
            return BitmapFactory.decodeStream(new FileInputStream(imageFile(i)));
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    File getPicDir() {
        File rootsd = Environment.getExternalStorageDirectory();
        return new File(rootsd, "/DCIM/Camera");
    }

    List<String> listAllPictureNames() {
        List<String> result = new ArrayList<String>();
        listPictureNames(mPicDir, "", result);
        return result;
    }

    void listPictureNames(File dir, String path, List<String> result) {
        assert (dir.isDirectory());
        for (File f : dir.listFiles()) {
            String name = (path.length() == 0 ? "" : path + "/") + f.getName();
            if (f.isFile()) {
                result.add(name);
            } else {
                listPictureNames(f, name, result);
            }
        }
    }
}
