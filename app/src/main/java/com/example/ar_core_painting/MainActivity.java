package com.example.ar_core_painting;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.util.List;

public class MainActivity extends AppCompatActivity {


    Session mSession;
    GLSurfaceView mySurView;
    MainRenderer mRenderer;
    Config mConfig;  //ARCore session 설정정보를 받을 변수

    onClick onClick = new onClick();
    SeekbarController seekbarControl = new SeekbarController();
    MainRenderCallBack callBack;

    Button[] btnColors = new Button[3];
    int colorIds[] = {R.id.btn_green, R.id.btn_red, R.id.btn_blue};
    int colorStat = 0;
    float lineStat = 50.0f;
    SeekBar seekBar;
    Button btn_remove;
    CheckBox checkBox;


    // 이전 점을 받을 배열열
    float[] lastPoint = {0.0f, 0.0f, 0.0f};
    float[] projMatrix = new float[16];
    float[] viewMatrix = new float[16];

    float same_dist = 0.001f;
    float displayX, displayY;
    //    새로운 선인지,        점 추가인지?
    boolean newPath = false, pointAdd = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mySurView = (GLSurfaceView) findViewById(R.id.glsurfaceview);
        btn_remove = (Button) findViewById(R.id.btnReset);
        checkBox = (CheckBox) findViewById(R.id.chk_flattening);
        seekBar = (SeekBar) findViewById(R.id.seekbar_colorControl);

        for(int i =0; i<btnColors.length; i++) {
            btnColors[i] = (Button) findViewById(colorIds[i]);
            btnColors[i].setOnClickListener(onClick);
        }

        btn_remove.setOnClickListener(onClick);
        seekBar.setOnSeekBarChangeListener(seekbarControl);

        //MainActivity의 화면 관리 메니져  --> 화면변화를 감지 :: 현재 시스템에서 서비스 지원
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);


        //화면 변화가 발생되면 MainRenderer의 화면변환을 실행시킨다.
        if (displayManager != null) {
            //화면에 대한 리스너 실행
            displayManager.registerDisplayListener(
                    //익명클래스로 정의
                    new DisplayManager.DisplayListener() {
                        @Override
                        public void onDisplayAdded(int i) {}
                        @Override
                        public void onDisplayRemoved(int i) {}
                        //화면이 변경되었다면
                        @Override
                        public void onDisplayChanged(int i) {
                            synchronized (this) {
                                //화면 갱신 인지 메소드 실행
                                mRenderer.onDisplayChanged();
                            }
                        }
                    }, null);
        }


        callBack = new MainRenderCallBack();

        mRenderer = new MainRenderer(callBack);
        // pause 시 관련 데이터가 사라지는 것을 막는다.
        mySurView.setPreserveEGLContextOnPause(true);
        mySurView.setEGLContextClientVersion(2); //버전을 2.0 사용

        //화면을 그리는 Renderer를 지정한다.
        // 새로 정의한 MainRenderer를 사용한다.
        mySurView.setRenderer(mRenderer);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mySurView.onPause();
        mSession.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraPermission();
        try {
            if (mSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, true)) {
                    case INSTALLED:// ARCore 정상설치 되었음
                        //ARCOre 가 정상설치 되어서 session 을 생성가능한 형태임
                        mSession = new Session(this);
                        Log.d("session 인감", "session  생성이여!!!");
                        break;
                    case INSTALL_REQUESTED:// ARCore 설치 필요
                        Log.d("session 인감", "ARCore  INSTALL_REQUESTED");
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //화면 갱신시 세션설정 정보를 받아서 내세션의 설정으로 올린다.
        mConfig = new Config(mSession);
        mSession.configure(mConfig);

        try {
            mSession.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        mySurView.onResume();

        //랜더링 계속 호출
        mySurView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {

        displayX = event.getX();
        displayY = event.getY();

        switch (event.getAction()) {
            // 처음 찍은 점의 좌표
            case MotionEvent.ACTION_DOWN:
                newPath = true; // 새로운 선
                pointAdd = true; // 새로운 점점
                break;

            // 선을 그리는중
            case MotionEvent.ACTION_MOVE:
                pointAdd = true;
                break;

            // 손을 뗏다
            case MotionEvent.ACTION_UP:
                pointAdd = false;
                break;
        }
        return true;
    }

    //카메라 퍼미션 요청
    void cameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    0
            );
        }
    }

    void hideStatusBar() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
    }

    public boolean sameChk(float x, float y, float z) {
        float dx = x - lastPoint[0];
        float dy = y - lastPoint[1];
        float dz = z - lastPoint[2];
        boolean res = Math.sqrt(dx * dx * +dy * dy + dz * dz) > same_dist;
        Log.d("sameChk : ", res + "");
        return res;
    }

    public void removeBtnGo(View view) {
        mRenderer.removePath();
    }

    //                         현재 좌표    ,         크   기          ,               매트릭스
    float[] getScreenPoint(float x, float y, float width, float height, float[] projMatrix,
                           float[] viewMatrix) {
        float[] position = new float[3];
        float[] direction = new float[3];

        x = x * 2 / width - 1.0f;
        y = (height - y) * 2 / height - 1.0f;

        float[] viewProjMat = new float[16];
        Matrix.multiplyMM(viewProjMat, 0, projMatrix, 0, viewMatrix, 0);

        float[] invertedMat = new float[16];
        Matrix.setIdentityM(invertedMat, 0);
        Matrix.invertM(invertedMat, 0, viewProjMat, 0);

        float[] farScreenPoint = new float[]{x, y, 1.0F, 1.0F};
        float[] nearScreenPoint = new float[]{x, y, -1.0F, 1.0F};
        float[] nearPlanePoint = new float[4];
        float[] farPlanePoint = new float[4];

        Matrix.multiplyMV(nearPlanePoint, 0, invertedMat, 0, nearScreenPoint, 0);
        Matrix.multiplyMV(farPlanePoint, 0, invertedMat, 0, farScreenPoint, 0);

        position[0] = nearPlanePoint[0] / nearPlanePoint[3];
        position[1] = nearPlanePoint[1] / nearPlanePoint[3];
        position[2] = nearPlanePoint[2] / nearPlanePoint[3];

        direction[0] = farPlanePoint[0] / farPlanePoint[3] - position[0];
        direction[1] = farPlanePoint[1] / farPlanePoint[3] - position[1];
        direction[2] = farPlanePoint[2] / farPlanePoint[3] - position[2];

        //이건 평면을 만드는거 같다
        normalize(direction);

        position[0] += (direction[0] * 0.1f);
        position[1] += (direction[1] * 0.1f);
        position[2] += (direction[2] * 0.1f);

        return position;
    }

    //평면을 만드는 거?
    private void normalize(float[] v) {
        double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= norm;
        v[1] /= norm;
        v[2] /= norm;
    }

    class MainRenderCallBack implements MainRenderer.RenderCallBack {

        @Override
        public void preRender() {
            //화면이 회전되었다면
            if (mRenderer.viewprotChanged) {
                //현재 화면 가져오기
                Display display = getWindowManager().getDefaultDisplay();
                mRenderer.updateSession(mSession, display.getRotation());
            }

            //session 객체와 연결해서 화면 그리기 하기
            mSession.setCameraTextureName(mRenderer.getTextureId());

            //화면 그리기에서 사용할 frame --> session 이 업데이트 되면 새로운 프레임을 받는다.
            Frame frame = null;

            try {
                frame = mSession.update();
            } catch (CameraNotAvailableException e) {
                e.printStackTrace();
            }

            //화면 (카메라정보)을 바꾸기 위한 작업
            mRenderer.mCamera.transformDisplayGeometry(frame);

            ////   ↓↓↓↓↓↓   PointCloud 설정구간
            //ARCore 에 정의된 클래스
            //현재 프레임에서 특정있는 점들에 대한  포인트 값( 3차원 좌표값 ) 을 받을 객체
            PointCloud pointCloud = frame.acquirePointCloud();

            // 포인트 값을 적용시키기위해 mainRenderer -> PointCloud.update() 실행
            mRenderer.mPointCloud.update(pointCloud);

            //사용이 끝난 포인트 자원해제
            pointCloud.release();

            List<HitResult> arr = frame.hitTest(displayX, displayY);



            /* 화면 터치시 작업 끝*/
            //카메라 frame 에서 받는다
            //--> mPointCloud 에서 렌더링 할때 카메라의 좌표계산을 받아서 처리
            Camera camera = frame.getCamera();

            camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100.0f);
            camera.getViewMatrix(viewMatrix, 0);

            //mRenderer.mPointCloud.updateMatrix(viewMatrix, projMatrix);
            mRenderer.updateProjMatrix(projMatrix);
            mRenderer.updateViewMatrix(viewMatrix);


            // 선 그리기
            if (checkBox.isChecked()) {
                float[] screenPoint = getScreenPoint(displayX, displayY, mRenderer.width, mRenderer.height, projMatrix, viewMatrix);
                // 스크린 그리기 상태
                if (newPath) {
                    newPath = false;
                    mRenderer.addLine(screenPoint[0], screenPoint[1], screenPoint[2], colorStat, lineStat);
                    for (int i = 0; i < lastPoint.length; i++)
                        lastPoint[i] = screenPoint[i];

                } else if (pointAdd) { // 점 추가 라면?
                    pointAdd = false;
                    if (sameChk(screenPoint[0], screenPoint[1], screenPoint[2])) {
                        // 새로운 점점 그리기
                        mRenderer.addPoint(screenPoint[0], screenPoint[1], screenPoint[2]);
                        for (int i = 0; i < lastPoint.length; i++)
                            lastPoint[i] = screenPoint[i];
                    }
                }
            } else {
                /* 화면 터치시 작업 시작*/
                if (newPath) {
                    newPath = false;
                    int i = 0;
                    for (HitResult hr : arr) {
                        Pose pose = hr.getHitPose();
                        // 새로운 라인 그리기
                        mRenderer.addLine(pose.tx(), pose.ty(), pose.tz(), colorStat, lineStat);
                        lastPoint[0] = pose.tx();
                        lastPoint[1] = pose.ty();
                        lastPoint[2] = pose.tz();
                        break;
                    }
                } else if (pointAdd) { // 점 추가 라면?
                    pointAdd = false;
                    int i = 0;
                    for (HitResult hr : arr) {
                        Pose pose = hr.getHitPose();
                        if (sameChk(pose.tx(), pose.ty(), pose.tz())) {
                            // 새로운 점점 그리기
                            mRenderer.addPoint(pose.tx(), pose.ty(), pose.tz());
                            lastPoint[0] = pose.tx();
                            lastPoint[1] = pose.ty();
                            lastPoint[2] = pose.tz();
                            break;
                        }
                    }
                }
            }


        }
    }

    class onClick implements View.OnClickListener{
        @Override
        public void onClick(View view) {
            if(view.getId() == colorIds[0])
                colorStat = 1;
            else if(view.getId() == colorIds[1])
                colorStat = 2;
            else if(view.getId() == colorIds[2])
                colorStat = 3;
            else if(view.getId() == R.id.btnReset)
                removeBtnGo(view);

        }
    }
    class SeekbarController implements SeekBar.OnSeekBarChangeListener{

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
            lineStat = progress;
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    }

}
