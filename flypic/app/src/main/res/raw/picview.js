
function list_pics(f) {
    jQuery.get('/pics', function (pics) {
        f(pics.map(function (s) { return s.replace(/^\/+/, ''); }));
    }, 'json');
}

function show_pic(i) {
    var img = $('#currentImage');
    img[0].onload = function () { positionImage(0); };
    img.attr('src', "/pic/" + PictureNames[i]);
}

LOG_CONTENTS = [];
function log(msg) {
    LOG_CONTENTS.push(msg);
    updateLog();
}
function updateLog() {
    var contents = [];
    var max = LOG_CONTENTS.length;
    if (max > 5) max = 5;
    for (var i = LOG_CONTENTS.length - max; i < LOG_CONTENTS.length; i++) {
        contents.push(LOG_CONTENTS[i]);
        contents.push("<br/>");
    }
    $('#logView')[0].innerHTML = contents.join("");
}

var Info;
var PictureNames;
var Sock;

function initPicView() {
    jQuery.get('/info', function (obj) {
        Info = obj;
        list_pics(function (pics) {
            PictureNames = pics;
            show_pic(0);
        });
        Sock = openWebSocket();
    }, 'json');
}

var MSGS = [];
function openWebSocket() {
    var ws = new WebSocket(Info.wsd);
    ws.onmessage = function (msg) {
        var obj = JSON.parse(msg.data.replace(/\0/g, ''));
        if (obj.type == 'move') {
            var delta = obj.delta;
            var left = $('#currentImageDiv').offset().left;
            console.log("Old left = " + (left));
            console.log("Setting new left = " + (left + delta));
            $('#currentImageDiv').offset({'left': left+delta});
        }
        //MSGS.push(msg.data.replace(/\0/g,''));
        log("WEBSOCKET_DATA " + typeof(msg.data) + ": " + msg.data);
    }
    return ws;
}

var CURPOS;
function positionImage(offset) {
    CURPOS = offset;
    var winWidth = $(window).width();
    var winHeight = $(window).height();

    var img = $('#currentImage');
    var imgWidth = img.width();
    var imgHeight = img.height();

    var centerWidth = winWidth / 2;
    var centerHeight = winHeight / 2;

    // An offset of -1 puts the right-side of the image
    // on the left edge of the page (image xpos = -imgWidth)
    // An offset of 1 puts the left-side of the image
    // on the right side of the page (image xpos = winWidth)
    var normOffset = (offset + 1) / 2;
    var xpos = (((imgWidth + winWidth) * normOffset) - imgWidth)|0;
    var imgContainer = $('#currentImageDiv');
    var ypos = (centerHeight - imgHeight/2)|0;
    console.log("Setting position = " + xpos + "px, " + ypos + "px");
    imgContainer.css('left', xpos + 'px');
    imgContainer.css('top', ypos + 'px');
}

function animateMove(offset) {
    // Move time is 0.5 seconds.
    // Update every 0.01 seconds (10 ms)
    var origPos = CURPOS;
    var span = 500;
    var interval = 10;
    var startTime = Date.now();
    var endTime = startTime + span;
    function updatePos() {
        var cur = Date.now();
        var fracChange = (cur - startTime) / span;
        var posn = origPos + (offset * fracChange);
        positionImage(posn);
        if (cur < endTime)
            setTimeout(updatePos, interval);
    }
    updatePos();
}