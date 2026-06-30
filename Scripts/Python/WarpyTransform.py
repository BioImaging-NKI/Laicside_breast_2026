import numpy as np
import skimage as ski

class Warpytransform:
    def __init__(self, transformdata):
        self.affine_1 = self._reshape_affine(np.array(transformdata['realTransform_0']['affinetransform3d']))
        self.i_affine_1 = np.linalg.inv(self.affine_1)
        tps = transformdata['realTransform_1']['wrappedTransform']['wrappedTransform']
        self.affine_2 = self._reshape_affine(np.array(transformdata['realTransform_2']['affinetransform3d']))
        self.i_affine_2 = np.linalg.inv(self.affine_2)
        self.affine_c = np.dot(self.affine_1, self.affine_2)
        srcPts = np.transpose(np.array(tps['srcPts']))
        tgtPts = np.transpose(np.array(tps['tgtPts']))
        self.tps = ski.transform.ThinPlateSplineTransform()
        self.tps.estimate(srcPts, tgtPts)
        self.i_tps = ski.transform.ThinPlateSplineTransform()
        self.i_tps.estimate(tgtPts, srcPts)
        affine_tps_approx = np.linalg.lstsq(np.hstack((srcPts, np.ones((srcPts.shape[0],1)))), tgtPts)[0]
        self.affine_tps = np.hstack((affine_tps_approx,np.array([[0,0,1]]).T))
    def _reshape_affine(self, array_in):
        return np.transpose(np.array([[array_in[0], array_in[1],array_in[3]],
                         [array_in[4], array_in[5],array_in[7]],
                         [0.0, 0.0,1.0]]))
    def transform(self, coords):
        c = np.hstack((coords, np.ones((coords.shape[0],1))))
        c = np.dot(c, self.affine_1)
        c = self.tps(c[:,0:2])
        c = np.hstack((c, np.ones((c.shape[0],1))))
        c = np.dot(c, self.affine_2)
        return c[:,0:2]
    def inverse_transform(self, coords):
        c = np.hstack((coords, np.ones((coords.shape[0],1))))
        c = np.dot(c, self.i_affine_2)
        c = self.i_tps(c[:,0:2])
        c = np.hstack((c, np.ones((c.shape[0],1))))
        c = np.dot(c, self.i_affine_1)
        return c[:,0:2]
    def transform_affine(self,coords):
        c = np.hstack((coords, np.ones((coords.shape[0],1))))
        c = np.dot(c, self.affine_c)
        return c[:,0:2]
    def transform_affine2(self,coords):
        c = np.hstack((coords, np.ones((coords.shape[0],1))))
        c = np.dot(np.dot(np.dot(c, self.affine_1), self.affine_tps), self.affine_2)
        return c[:,0:2]
