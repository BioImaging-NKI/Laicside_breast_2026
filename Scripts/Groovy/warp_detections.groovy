import net.imglib2.realtransform.InvertibleRealTransformSequence
import qupath.ext.warpy.Warpy
import qupath.lib.scripting.QP
import java.nio.file.Paths

// For some reason this takes about 51 seconds even for only 18k objects
def DO_INVERSE = true
def filename = QP.getCurrentImageNameWithoutExtension().substring(0,3)+"_transform.json"
def folder = "G:\\Horlings\\mc_hh\\BREAST_SAMPLES\\ALL_CORES_NEW_SEG\\transforms"
def transformfile = Paths.get(folder, filename).toFile()
InvertibleRealTransformSequence mytransform = Warpy.getRealTransform(transformfile) as InvertibleRealTransformSequence
def to_transform = QP.getAllObjects(false)
if (DO_INVERSE){
    from_transform = Warpy.transformPathObjects(to_transform, mytransform.inverse())
    QP.getCurrentHierarchy().removeObjects(to_transform, false)
    QP.addObjects(from_transform)
}else {
    from_transform = Warpy.transformPathObjects(to_transform, mytransform)
    QP.getCurrentHierarchy().removeObjects(to_transform, false)
    QP.addObjects(from_transform)
}

