//>>built
define("dgrid/util/has-css3",["dojo/has"],function(a){function e(d,b){var c=d.style,a;if(b in c)return!0;b=b.slice(0,1).toUpperCase()+b.slice(1);for(a=f.length;a--;)if(f[a]+b in c)return f[a];return!1}var f=["ms","O","Moz","Webkit"];a.add("css-transitions",function(a,b,c){return e(c,"transitionProperty")});a.add("css-transforms",function(a,b,c){return e(c,"transform")});a.add("css-transforms3d",function(a,b,c){return e(c,"perspective")});a.add("transitionend",function(){var d=a("css-transitions");
return!d?!1:!0===d?"transitionend":{ms:"MSTransitionEnd",O:"oTransitionEnd",Moz:"transitionend",Webkit:"webkitTransitionEnd"}[d]});return a});
//# sourceMappingURL=has-css3.js.map