var blobToUpload;

$("#resize-button").on("click", function () {
    uploadBlob(blobToUpload);
});

$.getJSON("/demo-images", function (data) {
    var dataArr = Array.from(data);
    dataArr.forEach(function (value, index) {
        var selector = "#preload-image" + (index + 1);
        urlToDataURI(value, function (dataURI) {
            $(selector).attr("src", dataURI);
        });
        $(selector).on("click", function () {
            $(".ui.modal").modal("show");
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
        $(".ui.modal").modal("show");
        var reader = new FileReader();
        reader.onload = (function () {
            return function (ev2) {
                $("#image-left").attr("src", ev2.target.result);
                blobToUpload = dataURItoBlob(ev2.target.result);
            }
        })(file);
        reader.readAsDataURL(file);
    } else {
        alert("The File APIs are not fully supported in this browser.");
    }
});

function urlToDataURI(url, callback) {
    var xhr = new XMLHttpRequest();
    xhr.onload = function () {
        var reader = new FileReader();
        reader.onloadend = function () {
            callback(reader.result);
        };
        reader.readAsDataURL(xhr.response);
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
    var formData = new FormData();
    formData.append("file", blob);
    $.ajax({
        url: "http://localhost:8000/upload",
        data: formData,
        processData: false,
        contentType: false,
        type: "POST",
        error: function (result) {
            throw new Error(result);
        }
    });
}