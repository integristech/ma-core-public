//>>built
define(["dojo/_base/declare","dojo/dom-class","dojo/dom-construct","dijit/_WidgetBase"],function(e,c,d,f){return e("dojox.mobile.ProgressBar",f,{value:"0",maximum:100,label:"",baseClass:"mblProgressBar",buildRendering:function(){this.inherited(arguments);this.progressNode=d.create("div",{className:"mblProgressBarProgress"},this.domNode);this.msgNode=d.create("div",{className:"mblProgressBarMsg"},this.domNode)},_setValueAttr:function(a){a+="";this._set("value",a);var b=Math.min(100,-1!=a.indexOf("%")?
parseFloat(a):this.maximum?100*a/this.maximum:0);this.progressNode.style.width=b+"%";c.toggle(this.progressNode,"mblProgressBarNotStarted",!b);c.toggle(this.progressNode,"mblProgressBarComplete",100==b);this.onChange(a,this.maximum,b)},_setLabelAttr:function(a){this.msgNode.innerHTML=a},onChange:function(){}})});
//# sourceMappingURL=ProgressBar.js.map