#@ File[] (label = "Input files", style = "file") inputFiles
#@ File (label = "Output directory", style = "directory") outputFolder
#@ Boolean (label = "Only calculate normalization values (do not save images)", value=true) onlyCalculateValues

displayMaxMultiplier = 1;	//Influences the max displayed value
imageMultiplier = 1000;		//Normalized images (8-bit/16-bit) will be multiplied by this value
normalizeChannels = true;
minimumHighIntensity = 200;	//Minimum intensity to prevent blowup of almost-empty channels - only used in the exported images
clipChannels = false;

//Percentile thresholds for 'low values' (background)
lowerPercentileLow = 0.00;
upperPercentileLow = 0.2;

//Percentile thresholds for 'high values' (highly positive cells / high staining levels)
lowerPercentileHigh = 0.95;
upperPercentileHigh = 0.999;

run("Set Measurements...", "area mean standard min centroid center integrated limit redirect=None decimal=3");
print("\\Clear");
run("Close All");

for (f=0; f<inputFiles.length; f++) {
	if(isOpen("Table_image")) selectImage("Table_image");
	close("\\Others");
	path = inputFiles[f];
    if (endsWith(path, ".tif") && !endsWith(path, ".ome.tif")) open(path);
    else run("Bio-Formats Importer", "open="+path+" autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
	
	original = getTitle();
	
	setBatchMode("hide");
	if (!endsWith(path, ".tif")) run("Stack to Hyperstack...", "order=xyczt(default) channels=27 slices=1 frames=1 display=Grayscale"); //Channels are not really channels yet in the exported .ome.tif images from QuPath
	Stack.setDisplayMode("grayscale");
	
	getDimensions(width, height, channels, slices, frames);
	minima = newArray(channels);
	maxima = newArray(channels);
	means = newArray(channels);
	//channelName = newArray(channels);
	
	run("Clear Results");
	for (i = 1; i <= channels; i++) {
		showStatus("Image "+f+1+"/"+inputFiles.length+", channel "+i+1+"/"+channels);
		showProgress(i, channels);
	    Stack.setChannel(i);
	    resetMinAndMax;
	
	    getStatistics(area, mean, min, max, std, histogram);
	    minima[i-1] = min;
	    maxima[i-1] = max;
	    means[i-1] = mean;
		
		threshold_percentile(lowerPercentileLow, upperPercentileLow, true);
		lowValues = getValue("Mean limit");
	
		threshold_percentile(lowerPercentileHigh, upperPercentileHigh, true);
		highValues = getValue("Mean limit");
		resetMinAndMax;
	
	    setResult("Nr.", i-1, i);
		setResult("Channel", i-1, getMetadata("Label"));
	    setResult("Min", i-1, min);
	    setResult("Max", i-1, max);
	    setResult("Mean", i-1, mean);
	    setResult("Low values", i-1, lowValues);
	    setResult("High values", i-1, highValues);
	    getMinAndMax(minDisplay, maxDisplay);
		setResult("MinDisplay", i-1, minDisplay);
		setResult("MaxDisplay", i-1, maxDisplay);
	    updateResults();
	}
	run("Results to Image");
	rename(f);

	if(f>0) run("Concatenate...", "  title=Table_image open image1=[Table_image] image2="+f);
	else rename("Table_image");

	selectImage(original);
}

selectImage("Table_image");
makeRectangle(5, 0, 2, channels);
run("Reslice [/]...", "output=1.000 start=Left avoid");
rename("High_and_low_values");
run("Reslice [/]...", "output=1.000 start=Top avoid");
//setBatchMode("show");

if(inputFiles.length > 1) run("Z Project...", "projection=Median");
makeRectangle(0, 0, channels, 1);
lowValuesMedian = getProfile();
makeRectangle(0, 1, channels, 1);
highValuesMedian = getProfile();
Table.create("Normalization_values");
Table.setColumn("Channel", Table.getColumn("Channel", "Results"));
Table.setColumn("Low", lowValuesMedian);
Table.setColumn("High", highValuesMedian);


//Dialog: select from all channels which to use in the segmentation
//nucleiArray = newArray("DAPI", "Histon H3");
nucleiArray = Table.getColumn("Channel");
membraneArray = Table.getColumn("Channel");
for (i = 0; i < membraneArray.length; i++) {
	nucleiArray[i] = IJ.pad(i+1,2)+"   "+nucleiArray[i];
	membraneArray[i] = IJ.pad(i+1,2)+"   "+membraneArray[i];
}
print(nucleiArray.length);

nucleiDefaults = newArray(nucleiArray.length);
nucleiDefaults[0]=1;

membraneDefaults = newArray(0,0,1,0,1,1,1,0,0,1,1,0,0,0,0,0,1,1,1,1,0,1,0,1,1,0,0,1,0,0,0);
membraneDefaults = newArray(0,0,1,0,1,1,1,0,0,1,1,0,0,0,0,0,1,1,1,1,0,1,0,1,1,0,0);
if(membraneDefaults.length < membraneArray.length) {
	membraneDefaults = Array.concat(membraneDefaults,newArray(membraneArray.length-membraneDefaults.length));
}
else if (membraneDefaults.length > membraneArray.length) {
	membraneDefaults = Array.trim(membraneDefaults, membraneArray.length);
}

Dialog.createNonBlocking("Select nuclei channels");
Dialog.addMessage("Select nuclei channels for segmentation");
Dialog.addCheckboxGroup(channels, 1, nucleiArray, nucleiDefaults);
Dialog.show();
nucleiArrayBooleans = newArray(channels);
for (i=0; i<channels; i++) nucleiArrayBooleans[i] = Dialog.getCheckbox();

Dialog.createNonBlocking("Select membrane channels");
Dialog.addMessage("Select membrane channels for segmentation");
Dialog.addCheckboxGroup(channels, 1, membraneArray, membraneDefaults);
Dialog.show();
membraneArrayBooleans = newArray(channels);
for (i=0; i<channels; i++) membraneArrayBooleans[i] = Dialog.getCheckbox();

file = "codex_channel_command.json";
if(File.exists(outputFolder+File.separator+file)) { File.delete(outputFolder+File.separator+file); print("\\Update:"); }
f = File.open(outputFolder+File.separator+file);

lowValue = Table.getColumn("Low");
highValue = Table.getColumn("High");

channels = Table.size;

//Write JSON file
print(f, "[\n");
for (i = 0; i < channels; i++) {
	if(i<channels-1) print(f, "{\n\"name\": \""+Table.getString("Channel", i)+"\",\n\"nuclear\": "+parseBoolean(nucleiArrayBooleans[i])+",\n\"membrane\": "+parseBoolean(membraneArrayBooleans[i])+",\n\"low\": "+lowValue[i]+",\n\"high\": "+highValue[i]+"\n},");
	else print(f, "{\n\"name\": \""+Table.getString("Channel", i)+"\",\n\"nuclear\": "+parseBoolean(nucleiArrayBooleans[i])+",\n\"membrane\": "+parseBoolean(membraneArrayBooleans[i])+",\n\"low\": "+lowValue[i]+",\n\"high\": "+highValue[i]+"\n}");
}
print(f, "]");

File.close(f);
print("JSON output file created");


if(!onlyCalculateValues) {
	for (f=0; f<inputFiles.length; f++) {
		open(inputFiles[f]);

		Stack.setChannel(1);
		for (i = 0; i < channels; i++) {
			showStatus("Creating normalized nuclei and membrane channels for image "+f+1+"/"+inputFiles.length);
			showProgress(f, inputFiles.length);
		    Stack.setChannel(i+1);

			//normalize channels
			run("Select None");
			run("Subtract...", "value="+lowValue[i]+" slice");
			run("Divide...", "value="+maxOf(highValue[i],minimumHighIntensity)/imageMultiplier+" slice");
			setMinAndMax(lowValue[i], imageMultiplier*displayMaxMultiplier);
			if(clipChannels == true) changeValues(imageMultiplier*displayMaxMultiplier, 65535, imageMultiplier*displayMaxMultiplier);
			else setMinAndMax(lowValue[i], highValue[i]*displayMaxMultiplier);

			nucleiChannels = addScalarToArray(indexOfArray(nucleiArrayBooleans, 1), 1);
			membraneChannels = addScalarToArray(indexOfArray(membraneArrayBooleans, 1), 1);
		}
		imageID = getImageID();

		sum_channels(imageID, nucleiChannels, "nuclei");
		sum_channels(imageID, membraneChannels, "membrane");

		run("Merge Channels...", "c1=nuclei c2=membrane create ignore");
		Stack.setChannel(2);
		run("Cyan");
		Stack.setChannel(1);
		run("Magenta");
		run("Enhance Contrast", "saturated=0.35");
		saveAs("Tiff", outputFolder + File.separator + File.getNameWithoutExtension(inputFiles[f]) + "_forSegmentation");
		close();
}



//Adds a scalar to all elements of an array
function addScalarToArray(array, scalar) {
	added_array=newArray(lengthOf(array));
	for (a=0; a<lengthOf(array); a++) {
		added_array[a]=array[a] + scalar;
	}
	return added_array;
}


//Returns, as array, the indices at which a value occurs within an array
function indexOfArray(array, value) {
	count=0;
	for (a=0; a<lengthOf(array); a++) {
		if (array[a]==value) {
			count++;
		}
	}
	if (count>0) {
		indices=newArray(count);
		count=0;
		for (a=0; a<lengthOf(array); a++) {
			if (array[a]==value) {
				indices[count]=a;
				count++;
			}
		}
		return indices;
	}
	return newArray(0);
}


function parseBoolean(number_input) {
	if(number_input == 1) return "true";
	if(number_input == 0) return "false";
}


function sum_channels(image, array, name) {
	selectImage(image);
	getDimensions(width, height, channels, slices, frames);

	concatenateString = "";
	for (i = 0; i < array.length; i++) {
		selectImage(image);
		run("Duplicate...", "title=channel_"+array[i]+" duplicate channels="+array[i]);
		concatenateString += "image"+i+1+"=channel_"+array[i]+" ";
	}
	if(array.length > 1) {
		run("Concatenate...", "  title=stack open "+concatenateString);
		run("Z Project...", "projection=[Sum Slices]");
		run("Enhance Contrast", "saturated=2");
		close("stack");
	}
	else run("32-bit");
	rename(name);
	return name;
}


//Double percentile threshold
function threshold_percentile(lowerPercentile, upperPercentile, ignoreZeros) {
	resetMinAndMax();
	getRawStatistics(nPixels, mean, min, max, std, histogram);
	if(ignoreZeros == true) nPixels = nPixels - histogram[0];
	lowerTotal = 0;
	upperTotal = nPixels;

	i=0;
	if(ignoreZeros == true) i=1;
	while (lowerTotal < nPixels*lowerPercentile) {
		lowerTotal += histogram[i];
		//print("lower percentile: "+lowerTotal / (nPixels - histogram[0]));
		i++;
	}
	j=histogram.length-1;
	while (upperTotal > nPixels*upperPercentile) {
		upperTotal -= histogram[j];
		//print("upper: "+j+", "+upperTotal / (nPixels - histogram[0]));
		j--;
	}
	setThreshold(i,j);
}
