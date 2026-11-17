'use strict';
/* Small offline WebGL OBJ viewer. Static geometry, optional Texture2D, NO Unity
   skeletal animation, MLBB shaders, or game runtime emulation. */
window.MeshViewer = class MeshViewer {
  constructor(canvas) {
    this.canvas=canvas;this.gl=canvas.getContext('webgl',{preserveDrawingBuffer:true,antialias:true});
    if(!this.gl)throw Error('WebGL tidak tersedia di browser ini');
    this.yaw=.45;this.pitch=-.15;this.zoom=3.5;this.vertices=0;this.hasTexture=0;
    this.initGL();this.attachControls();
  }
  shader(type,src){const gl=this.gl,s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
  initGL(){const gl=this.gl;
    const vert=`attribute vec3 aPosition; attribute vec3 aNormal; attribute vec2 aUV;
    uniform vec3 uCenter; uniform float uScale,uYaw,uPitch,uZoom,uAspect;
    varying vec2 vUV; varying vec3 vN;
    void main(){vec3 p=(aPosition-uCenter)*uScale; float cy=cos(uYaw),sy=sin(uYaw),cx=cos(uPitch),sx=sin(uPitch);
      vec3 q=vec3(p.x*cy-p.z*sy,p.y,p.x*sy+p.z*cy);
      vec3 r=vec3(q.x,q.y*cx-q.z*sx,q.y*sx+q.z*cx);
      float depth=max(0.25,uZoom-r.z);gl_Position=vec4(r.x*2.0/uAspect,r.y*2.0,0.0,depth);
      vec3 n=vec3(aNormal.x*cy-aNormal.z*sy,aNormal.y,aNormal.x*sy+aNormal.z*cy);
      vN=normalize(vec3(n.x,n.y*cx-n.z*sx,n.y*sx+n.z*cx)); vUV=aUV;}`;
    const frag=`precision mediump float; varying vec2 vUV; varying vec3 vN;
    uniform sampler2D uTexture;uniform float uHasTexture;
    void main(){vec3 light=normalize(vec3(0.5,0.9,0.7));float diffuse=0.38+0.62*abs(dot(normalize(vN),light));
      vec4 base=mix(vec4(0.64,0.63,0.91,1.0),texture2D(uTexture,vUV),uHasTexture);
      gl_FragColor=vec4(base.rgb*diffuse,base.a);}`;
    const prog=gl.createProgram();gl.attachShader(prog,this.shader(gl.VERTEX_SHADER,vert));gl.attachShader(prog,this.shader(gl.FRAGMENT_SHADER,frag));gl.linkProgram(prog);
    if(!gl.getProgramParameter(prog,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(prog));gl.useProgram(prog);this.program=prog;
    this.a={p:gl.getAttribLocation(prog,'aPosition'),n:gl.getAttribLocation(prog,'aNormal'),uv:gl.getAttribLocation(prog,'aUV')};
    this.u={center:gl.getUniformLocation(prog,'uCenter'),scale:gl.getUniformLocation(prog,'uScale'),yaw:gl.getUniformLocation(prog,'uYaw'),pitch:gl.getUniformLocation(prog,'uPitch'),zoom:gl.getUniformLocation(prog,'uZoom'),aspect:gl.getUniformLocation(prog,'uAspect'),has:gl.getUniformLocation(prog,'uHasTexture')};
    this.texture=gl.createTexture();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,this.texture);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([180,150,245,255]));
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
    gl.uniform1i(gl.getUniformLocation(prog,'uTexture'),0);gl.enable(gl.DEPTH_TEST);gl.disable(gl.CULL_FACE);
  }
  static readOBJ(source){
    const positions=[],uvs=[],normals=[],flat=[],boxMin=[Infinity,Infinity,Infinity],boxMax=[-Infinity,-Infinity,-Infinity];
    let triangleCount=0;
    function idx(v,count){const n=Number(v);return Number.isInteger(n)&&n!==0?(n>0?n-1:count+n):-1;}
    function vertex(spec){const parts=spec.split('/');const i=idx(parts[0],positions.length),j=parts[1]?idx(parts[1],uvs.length):-1,k=parts[2]?idx(parts[2],normals.length):-1;
      if(!positions[i])throw Error('Index vertex OBJ tidak valid');return {p:positions[i],uv:uvs[j]||[0,0],n:normals[k]||null};}
    for(const line of source.split(/\r?\n/)){
      const text=line.trim();if(!text||text[0]==='#')continue;
      if(text.startsWith('v ')){const p=text.slice(2).trim().split(/\s+/).map(Number).slice(0,3);if(p.length!==3||p.some(x=>!Number.isFinite(x)))throw Error('Vertex OBJ tidak valid');
        positions.push(p);for(let k=0;k<3;k++){boxMin[k]=Math.min(boxMin[k],p[k]);boxMax[k]=Math.max(boxMax[k],p[k]);}if(positions.length>300000)throw Error('Vertex melebihi batas preview');}
      else if(text.startsWith('vt ')){const a=text.slice(3).trim().split(/\s+/).map(Number);uvs.push([a[0]||0,a[1]||0]);}
      else if(text.startsWith('vn ')){const a=text.slice(3).trim().split(/\s+/).map(Number);normals.push(a.slice(0,3));}
      else if(text.startsWith('f ')){
        const parts=text.slice(2).trim().split(/\s+/);if(parts.length<3)continue;const poly=parts.map(vertex);
        for(let k=1;k<poly.length-1;k++){
          if(++triangleCount>100000)throw Error('Mesh melebihi 100.000 triangle untuk preview Android');
          const tri=[poly[0],poly[k],poly[k+1]],a=tri[0].p,b=tri[1].p,c=tri[2].p;
          const ab=[b[0]-a[0],b[1]-a[1],b[2]-a[2]],ac=[c[0]-a[0],c[1]-a[1],c[2]-a[2]];
          const fn=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]];
          for(const v of tri){flat.push(...v.p,...(v.n||fn),...v.uv);}
        }
      }
    }
    if(!triangleCount)throw Error('OBJ tidak mempunyai face triangle');
    const center=boxMin.map((min,i)=>(min+boxMax[i])/2),radius=Math.max(1e-5,...boxMin.map((min,i)=>(boxMax[i]-min)/2));
    return {flat:new Float32Array(flat),center,radius,triangles:triangleCount};
  }
  load(source){const parsed=MeshViewer.readOBJ(source),gl=this.gl;
    this.vertices=parsed.triangles*3;this.center=parsed.center;this.radius=parsed.radius;
    if(this.buffer)gl.deleteBuffer(this.buffer);this.buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,this.buffer);
    gl.bufferData(gl.ARRAY_BUFFER,parsed.flat,gl.STATIC_DRAW);const stride=8*4;
    for(const [attr,size,off] of [[this.a.p,3,0],[this.a.n,3,3*4],[this.a.uv,2,6*4]]){
      gl.enableVertexAttribArray(attr);gl.vertexAttribPointer(attr,size,gl.FLOAT,false,stride,off);
    }
    this.draw();return parsed;
  }
  async setTexture(blob){const img=new Image(),url=URL.createObjectURL(blob);
    try{await new Promise((resolve,reject)=>{img.onload=resolve;img.onerror=reject;img.src=url});
      const gl=this.gl;gl.bindTexture(gl.TEXTURE_2D,this.texture);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img);this.hasTexture=1;this.draw();
    }finally{URL.revokeObjectURL(url);}
  }
  attachControls(){let prev=null;this.canvas.addEventListener('pointerdown',e=>{prev=[e.clientX,e.clientY];this.canvas.setPointerCapture(e.pointerId)});
    this.canvas.addEventListener('pointermove',e=>{if(!prev)return;this.yaw+=(e.clientX-prev[0])/150;this.pitch=Math.max(-1.5,Math.min(1.5,this.pitch+(e.clientY-prev[1])/150));prev=[e.clientX,e.clientY];this.draw()});
    for(const evt of ['pointerup','pointercancel'])this.canvas.addEventListener(evt,()=>{prev=null});
    this.canvas.addEventListener('wheel',e=>{e.preventDefault();this.zoom=Math.max(1.5,Math.min(9,this.zoom+e.deltaY/170));this.draw()},{passive:false});
  }
  draw(){if(!this.vertices)return;const gl=this.gl,rect=this.canvas.getBoundingClientRect(),w=Math.max(1,Math.round(rect.width*devicePixelRatio)),h=Math.max(1,Math.round(rect.height*devicePixelRatio));
    if(this.canvas.width!==w||this.canvas.height!==h){this.canvas.width=w;this.canvas.height=h}gl.viewport(0,0,w,h);gl.clearColor(.07,.1,.19,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.useProgram(this.program);
    gl.uniform3fv(this.u.center,this.center);gl.uniform1f(this.u.scale,1/this.radius);gl.uniform1f(this.u.yaw,this.yaw);gl.uniform1f(this.u.pitch,this.pitch);
    gl.uniform1f(this.u.zoom,this.zoom);gl.uniform1f(this.u.aspect,w/h);gl.uniform1f(this.u.has,this.hasTexture);gl.drawArrays(gl.TRIANGLES,0,this.vertices);
  }
};
