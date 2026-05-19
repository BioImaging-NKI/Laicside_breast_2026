import qupath.lib.analysis.features.ObjectMeasurements
import qupath.lib.images.servers.ImageServer
import qupath.lib.scripting.QP
import java.awt.image.BufferedImage

def dets = QP.getDetectionObjects()
dets.parallelStream().forEach{it.getMeasurementList().clear()}
def intensitymeasurements = [ObjectMeasurements.Measurements.MEAN]
def compartments = [ObjectMeasurements.Compartments.CELL]
def myserver = QP.getCurrentServer() as ImageServer<BufferedImage>
//def myserver = new TransformedServerBuilder(QP.getCurrentServer() as ImageServer<BufferedImage>).deconvolveStains(QP.getCurrentImageData().getColorDeconvolutionStains()).build()
dets.parallelStream().forEach{ObjectMeasurements.addIntensityMeasurements(myserver, it, 1.0, intensitymeasurements, compartments)}
ObjectMeasurements.addShapeMeasurements(dets, QP.getCurrentServer().getPixelCalibration(), ObjectMeasurements.ShapeFeatures.AREA, ObjectMeasurements.ShapeFeatures.CIRCULARITY)
