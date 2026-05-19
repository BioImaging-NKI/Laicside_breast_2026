/* - Cleanup -


 */


import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.util.LinearComponentExtracter
import qupath.lib.objects.PathObject
import qupath.lib.scripting.QP
import java.util.stream.Collectors

// remove from border
def annos = QP.getAnnotationObjects()
annos.each( {anno->
    def geom = anno.getROI().getGeometry()
    def dets = anno.getChildObjects()
    def distancePixels = 1.0
    def toRemove = new HashSet<PathObject>()
    for (LineString line in (LinearComponentExtracter.getLines(geom) as List<LineString>)) {
        toRemove.addAll(dets.parallelStream()
                .filter(d -> line.isWithinDistance(d.getROI().getGeometry(), distancePixels))
                .collect(Collectors.toList()) as Collection<? extends PathObject>
        )
    }
    print("Removed "+toRemove.size()+" cells from border.")
    QP.removeObjects(toRemove)
})

// Remove detections smaller than 1 pixel
def removeobj0 = QP.getDetectionObjects().findAll({
    it.getROI().getArea() < 1
})
QP.removeObjects(removeobj0)
print("Removed "+removeobj0.size()+" detections <1 pixel.")

// Add shape measurements
QP.selectDetections()
QP.addShapeMeasurements("AREA", "CIRCULARITY")
QP.deselectAll()
// Remove too small cells
def removeobj = QP.getDetectionObjects().findAll({
    it.getMeasurementList().get("Area µm^2") < 5
})
QP.removeObjects(removeobj)
print("Removed "+removeobj.size()+" to small cells.")

// Fix missing circularity
def fixobj = QP.getDetectionObjects().findAll({
    it.getMeasurementList().get("Circularity") == Double.NaN
})
fixobj.each({
    it.getMeasurementList().put("Circularity", -1.0)
})
print("Fixed "+fixobj.size()+" cell circularities.")
