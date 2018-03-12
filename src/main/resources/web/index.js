var blobToUpload;

$("#resize-button").on("click", function () {
    var width = $("#width").val();
    var height = $("#height").val();
    try {
        width = parseInt(width);
        height = parseInt(height);
        var image = $("#image-left");
        var imgWidth = image[0].naturalWidth;
        var imgHeight = image[0].naturalHeight;
        var size = parseInt(image.attr("data-size"));
        if (size > 1) {
            var message = "image size must be less than 1MB";
            alert(message);
            throw new Error(message);
        }
        if ((imgWidth > 600 || imgHeight > 600)) {
            message = "image must be less than 600px by 600px";
            alert(message);
            throw new Error(message);
        }
        if ((width > imgWidth * 1.3 || width < imgWidth * 0.7)
            || (height > imgHeight * 1.3 || height < imgHeight * 0.7)) {
            message = "width and height must be within 30% of the image dimensions";
            alert(message);
            throw new Error(message);
        }
    } catch (e) {
        return;
    }
    $("#resize-button").addClass("disabled");
    uploadBlob(blobToUpload);
});

function showModal() {
    $(".ui.modal")
        .modal({
            closable: false,
            onVisible: function () {
                $("#your-modal").modal("refresh");
            }
        })
        .modal("show");
}

$.getJSON("/demo-images", function (data) {
    var dataArr = Array.from(data);
    dataArr.forEach(function (value, index) {
        var selector = "#preload-image" + (index + 1);
        urlToDataURI(value, function (dataURI) {
            $(selector).attr("src", dataURI);
        });
        $(selector).on("click", function () {
            $("#image-right").attr("src", "https://semantic-ui.com/images/wireframe/image.png");
            changeProgress(0);
            $("#resize-button").removeClass("disabled");
            showModal();
            var dri = $(selector).attr("src");
            $("#image-left").attr("src", dri);
            blobToUpload = dataURItoBlob(dri);
        });
    });
});

$("#file").on("change", function (ev) {
    if (window.File && window.FileReader && window.FileList && window.Blob) {
        var file = ev.target.files[0];
        if (!(file.type.match("image/jpeg") || file.type.match("image/png"))) return;
        $("#image-right").attr("src", "https://semantic-ui.com/images/wireframe/image.png");
        changeProgress(0);
        $("#resize-button").removeClass("disabled");
        showModal();
        var reader = new FileReader();
        reader.onload = (function () {
            return function (ev2) {
                var selector = $("#image-left");
                selector.attr("src", ev2.target.result);
                selector.attr("data-size", file.size / (1024 * 1024));
                blobToUpload = dataURItoBlob(ev2.target.result);
            }
        })(file);
        reader.readAsDataURL(file);
    } else {
        alert("The File APIs are not fully supported in this browser.");
    }
});

$("#image-left").on("load", function (ev) {
    var element = ev.target;
    $("#image-left-resol").text("Original (" + element.naturalWidth + "px X " +
        element.naturalHeight + "px)");
});

$("#image-right").on("load", function (ev) {
    var element = ev.target;
    $("#image-right-resol").text("Resized (" + element.naturalWidth + "px X " +
        element.naturalHeight + "px)");
});

function urlToDataURI(url, callback) {
    var xhr = new XMLHttpRequest();
    xhr.onload = function () {
        var reader = new FileReader();
        reader.onloadend = function () {
            callback(reader.result);
        };
        var blob = xhr.response;
        $("#image-left").attr("data-size", blob / (1024 * 1024));
        reader.readAsDataURL(blob);
    };
    xhr.open("GET", url);
    xhr.responseType = "blob";
    xhr.send();
}

function dataURItoBlob(dataURI) {
    if (typeof dataURI !== "string") {
        throw new Error("Invalid argument: dataURI must be a string");
    }
    dataURI = dataURI.split(",");
    var type = dataURI[0].split(":")[1].split(";")[0],
        byteString = atob(dataURI[1]),
        byteStringLength = byteString.length,
        arrayBuffer = new ArrayBuffer(byteStringLength),
        intArray = new Uint8Array(arrayBuffer);
    for (var i = 0; i < byteStringLength; i++) {
        intArray[i] = byteString.charCodeAt(i);
    }
    return new Blob([intArray], {
        type: type
    });
}

function uploadBlob(blob) {
    changeProgress(2);
    var formData = new FormData();
    formData.append("file", blob);
    formData.append("width", $("#width").val());
    formData.append("height", $("#height").val());
    $.ajax({
        url: "http://" + window.location.host + "/upload",
        data: formData,
        processData: false,
        contentType: false,
        type: "POST",
        success: function () {
            changeProgress(25);
        },
        error: function (result) {
            changeProgress(0);
            throw new Error(result);
        }
    });
}

function changeProgress(percent) {
    $("#progress").css("width", percent + "%");
}

var socket = new WebSocket("ws://" + window.location.host + "/ws");
socket.onerror = function () {
    console.log("Error in socket connection");
};
socket.onmessage = function (ev) {
    parseMessage(ev.data.toString())
};

var resultRegex = /(Status=\w+)\|(Filepath=\/[a-zA-Z0-9_.\/]+)/;

function validateResult(result) {
    return resultRegex.test(result);
}

function parseMessage(result) {
    if (!validateResult(result)) {
        return;
    }
    var m;
    var groups = [];
    if ((m = resultRegex.exec(result)) !== null) {
        m.forEach(function (value) {
            groups.push(value)
        })
    }
    var status = groups[1].split("=")[1];
    var reasonOrPath = groups[2].split("=")[1];
    if (status === "Failure") {
        alert("Failed: " + reasonOrPath);
        changeProgress(0)
    } else {
        var url = reasonOrPath.replace("/tmp/seam", "file");
        changeProgress(100);
        $("#image-right").attr("src", url);
    }
    $("#resize-button").removeClass("disabled");
}