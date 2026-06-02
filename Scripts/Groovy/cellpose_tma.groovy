import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatExpr
import org.bytedeco.opencv.opencv_core.Scalar
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import qupath.ext.biop.cellpose.Cellpose2D
import qupath.fx.dialogs.Dialogs
import qupath.lib.scripting.QP
import qupath.opencv.ops.ImageOp
import qupath.opencv.tools.OpenCVTools
import org.bytedeco.opencv.global.opencv_core
import qupath.lib.images.servers.PixelType
import java.lang.reflect.Type
import java.nio.file.Paths

def jsonfile = Paths.get(QP.getProject().getPath().parent as String, "codex_channel_command.json").toFile()

public class Channel {
    private boolean nuclear
    private boolean membrane
    private double low
    private double high
    public boolean isNuclear(){
        return nuclear
    }
    public boolean isMembrane(){
        return membrane
    }
    public double getLow(){
        return low
    }
    public double getRange(){
        return high-low
    }
}
JsonReader reader = new JsonReader(new FileReader(jsonfile))
Type CHANNEL_TYPE = new TypeToken<List<Channel>>() {}.getType()
Gson gson = new Gson()
List<Channel> channeldata = gson.fromJson(reader, CHANNEL_TYPE)

ArrayList<Integer> nucleus = []
ArrayList<Double> nucleus_low = []
ArrayList<Double>nucleus_range = []
ArrayList<Integer> membrane = []
ArrayList<Double> membrane_low = []
ArrayList<Double> membrane_range = []
channeldata.eachWithIndex{ Channel channel, int i ->
    if (channel.isMembrane()){
        membrane.add(i)
        membrane_low.add(channel.getLow())
        membrane_range.add(channel.getRange())
    }
    if (channel.isNuclear()){
        nucleus.add(i)
        nucleus_low.add(channel.getLow())
        nucleus_range.add(channel.getRange())
    }
}
print("Nucleus channels "+ nucleus)
print("Nucleus low "+ nucleus_low)
print("Nucleus range "+ nucleus_range)
print("Membrane channels "+ membrane)
print("Membrane low "+ membrane_low)
print("Membrane range "+ membrane_range)

//QP.clearAllObjects();
//QP.createFullImageAnnotation(true)
pathModel = 'tissuenet_cp3'
def cellpose = Cellpose2D.builder( pathModel )
        .pixelSize( 0.5 )             // Resolution for detection in um
        .channels( )	      // Select detection channel(s)
        .preprocess(new AddChannelsOp(
                nucleus.toList() as int[],
                nucleus_range.toList() as double[],
                nucleus_low.toList() as double[],
                membrane.toList() as int[],
                membrane_range.toList() as double[],
                membrane_low.toList() as double[]))                // List of preprocessing ImageOps to run on the images before exporting them
        .normalizePercentiles(0.1,99.8)
//        .normalizePercentilesGlobal(0.1, 99.8, 10) // Convenience global percentile normalization. arguments are percentileMin, percentileMax, dowsample.
        .tileSize(1024)                  // If your GPU can take it, make larger tiles to process fewer of them. Useful for Omnipose
        .cellposeChannels(1,2)         // Overwrites the logic of this plugin with these two values. These will be sent directly to --chan and --chan2
        .cellprobThreshold(0.0)        // Threshold for the mask detection, defaults to 0.0
//        .flowThreshold(0.4)            // Threshold for the flows, defaults to 0.4
        .diameter(16)                    // Median object diameter. Set to 0.0 for the `bact_omni` model or for automatic computation
//        .addParameter("save_flows")      // Any parameter from cellpose not available in the builder. See https://cellpose.readthedocs.io/en/latest/command.html
//        .addParameter("anisotropy", "3") // Any parameter from cellpose not available in the builder. See https://cellpose.readthedocs.io/en/latest/command.html
//        .cellExpansion(5.0)              // Approximate cells based upon nucleus expansion
//        .cellConstrainScale(1.5)       // Constrain cell expansion using nucleus size
//        .classify("My Detections")     // PathClass to give newly created objects
//        .measureShape()                // Add shape measurements
        .measureIntensity()             // Add cell measurements (in all compartments)
//        .createAnnotations()           // Make annotations instead of detections. This ignores cellExpansion
        .simplify(0)                   // Simplification 1.6 by default, set to 0 to get the cellpose masks as precisely as possible
        .build()

// Run detection for the selected objects
def imageData = QP.getCurrentImageData()
// def pathObjects = QP.getSelectedObjects()
def pathObjects = QP.getAnnotationObjects()
if (pathObjects.isEmpty()) {
    Dialogs.showErrorMessage("Cellpose", "Please select a parent object!")
    return
}
cellpose.detectObjects(imageData, pathObjects)
println 'Done!'

import qupath.lib.scripting.QP

def THRESHOLD = 2500
def MEASUREMENT = "DAPI: Mean"
def hierarchy = QP.getCurrentHierarchy()
def detobj = hierarchy.getDetectionObjects()
def removeobj = detobj.findAll({
    it.getMeasurementList().get(MEASUREMENT) < THRESHOLD
})
print("Removed "+removeobj.size()+" cells.")
hierarchy.removeObjects(removeobj, false)
QP.fireHierarchyUpdate()


class AddChannelsOp implements ImageOp {
    private int[] nucleus
    private int[] membrane
    private double[] nucleus_range
    private double[] nucleus_low
    private double[] membrane_range
    private double[] membrane_low
    private static final Logger logger = LoggerFactory.getLogger("AddChannelsOp")
    AddChannelsOp(int[] nucleus,  double[] nucleus_range,  double[] nucleus_low,
                  int[] membrane, double[] membrane_range, double[] membrane_low) {
        this.nucleus = nucleus
        this.nucleus_range = nucleus_range
        this.nucleus_low = nucleus_low
        this.membrane = membrane
        this.membrane_range = membrane_range
        this.membrane_low = membrane_low
    }
    public PixelType getOutputType(PixelType inputType){
        return PixelType.FLOAT32
    }
    public Mat apply(Mat input16) {
        def tofloat = ImageOps.Core.ensureType(PixelType.FLOAT32)
        def input = tofloat.apply(input16)
        def channels = OpenCVTools.splitChannels(input)
        logger.info('Found '+channels.size()+ ' channels.')
        logger.info("AAAAAAH")
        if (this.nucleus.max()>=channels.size()){
            throw new Error("Nucleus channel is larger than available channels.")
        }
        if (this.membrane.max()>=channels.size()){
            throw new Error("Membrane channel is larger than available channels.")
        }
        if (this.nucleus.size() != this.nucleus_range.size()){
            throw new Error("nucleus channels and weights not same size.")
        }
        if (this.nucleus.size() != this.nucleus_low.size()){
            throw new Error("nucleus channels and weights not same size.")
        }
        if (this.membrane.size() != this.membrane_range.size()){
            throw new Error("Membrane channels and weights not same size.")
        }
        if (this.membrane.size() != this.membrane_low.size()){
            throw new Error("Membrane channels and weights not same size.")
        }
        logger.info('Sanity check success. Summing channels')
        def sum_nuc = opencv_core.multiply(opencv_core.subtract(channels[this.nucleus[0]], new Scalar(this.nucleus_low[0])), 1/this.nucleus_range[0])
        if (this.nucleus.size() > 1){
            List<MatExpr> scaled_nuc = []
            for (int i = 0; i < this.nucleus.size(); i++) {
                scaled_nuc.add(opencv_core.multiply(opencv_core.subtract(channels[this.nucleus[i]], new Scalar(this.nucleus_low[i])), 1/this.nucleus_range[i]))
            }
            sum_nuc = opencv_core.add(scaled_nuc[0], scaled_nuc[1])
            for (int i = 2; i < this.nucleus.size(); i++) {
                sum_nuc = opencv_core.add(sum_nuc, scaled_nuc[i])
            }
        }
        def sum_mem = opencv_core.multiply(opencv_core.subtract(channels[this.membrane[0]], new Scalar(this.membrane_low[0])), 1/this.membrane_range[0])
        if (this.membrane.size() > 1){
            List<MatExpr> scaled_mem = []
            for (int i = 0; i < this.membrane.size(); i++) {
                scaled_mem.add(opencv_core.multiply(opencv_core.subtract(channels[this.membrane[i]], new Scalar(this.membrane_low[i])), 1/this.membrane_range[i]))
            }
            sum_mem = opencv_core.add(scaled_mem[0], scaled_mem[1])
            for (int i = 2; i < this.membrane.size(); i++) {
                sum_mem = opencv_core.add(sum_mem, scaled_mem[i])
            }
        }
        def combined_channels = [sum_mem.asMat(), sum_nuc.asMat()] as Collection<? extends Mat>
        return OpenCVTools.mergeChannels(combined_channels, null)
    }
}
