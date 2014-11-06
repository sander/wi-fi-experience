#include <Adafruit_MPR121.h>
#include <FeedbackServo.h>
#include <Servo.h>
#include <Wire.h>

const int n = 2;
FeedbackServo servo[n];

const int touch_pin = 4;
const int touch_start = 180;
const int touch_threshold = 20;

const int ir_led_pin = 13;
const int ir_sense_pin = A0;
const int ir_threshold = 20;

int i = 0;

Adafruit_MPR121 cap = Adafruit_MPR121();

void setup() {
  Serial.begin(115200);
  
  pinMode(ir_led_pin, OUTPUT);
  
  if (!cap.begin(0x5A)) {
    Serial.println("MPR121 not found, check wiring?");
    while (1);
  }

  servo[0].begin(10, A2);
  servo[1].begin(9, A3);
}

void loop() {
  while (Serial.available() > 0) {
    int val = Serial.read();
    if (val == 255) {
      i = 0;
    }
    else {
      servo[i].set(i == 0 ? val : 180 - val);
      i = (i + 1) % n;
    }
  }
  servo[0].loop();
  servo[1].loop();
  
  setTouch(cap.filteredData(touch_pin));
  setProximity(pulse());
  sendStatus();
}

int pulse() {
  // Adapted from Herman Aartsen's IRranger.pde.
  int iHpulse;
  int iLpulse;
  
  iLpulse = analogRead(ir_sense_pin); // measure low signal (=reference)
  digitalWrite(ir_led_pin, HIGH);     // turn the LED on (HIGH is the voltage level)
  delayMicroseconds(60);              // let signal settle
  iHpulse = analogRead(ir_sense_pin); // measure high signal
  digitalWrite(ir_led_pin, LOW);      // turn the LED on (HIGH is the voltage level)

  return iHpulse - iLpulse;
}

int last_touched = 0;
void setTouch(int touch) {
  //Serial.print("touch: ");
  //Serial.println(touch);
  
  //int touched = (touch < touch_start - touch_threshold) ? 1 : 0;
  if (touch != last_touched) {
    last_touched = touch;
  }
}

int last_proximity = 0;
void setProximity(int proximity) {
  if (abs(proximity - last_proximity) > ir_threshold) {
    last_proximity = proximity;
  }
}

int last_status = 0;
void sendStatus() {

  //if (last_touched == 0) return;
  int proximity_constrained = 127 - constrain(map(last_proximity, 142, 354, 0, 127), 0, 127);
    /*
  int status = last_touched * 128 + proximity_constrained;
  if (status != last_status) {
    Serial.write(status);
    last_status = status;
  }
  */
  Serial.write(0);
  Serial.write(last_touched);
  Serial.write(proximity_constrained);
}
